package implosion

import (
	"crypto/sha256"
	"fmt"
	"regexp"
	"sort"
	"strings"

	"github.com/mr-tron/base58"

	cement "github.com/mabels/cement/go"
)

type Scope struct {
	Name     string
	Checksum string
	Tags     []string
}

type ImplosionParams struct {
	Scopes []Scope
}

type ScopedTags struct {
	Name string
	Tags []string
}

type ValidScope struct {
	Name      string
	Checksum  string
	Tag2index map[string]int
	Tags      []string // ordered
}

func uarray2bits(uarray []byte) string {
	return base58.Encode(uarray)
}

func hexHash(s string) string {
	h := sha256.New()
	h.Write([]byte(s))
	return uarray2bits(h.Sum(nil))
}

func getScopeHash(tags []string) string {
	var hashed []string
	for _, tag := range tags {
		hashed = append(hashed, hexHash(tag))
	}
	sort.Strings(hashed)
	return hexHash(strings.Join(hashed, ""))
}

func removeDuplicates(arr []string) []string {
	set := make(map[string]int)
	idx := 0
	for _, v := range arr {
		_, ok := set[v]
		if !ok {
			set[v] = idx
			idx++
		}
	}
	result := make([]string, len(set))
	for key, value := range set {
		result[value] = key
	}
	return result
}

var reName = regexp.MustCompile(`[\[\]]`)

type tagIdxHash struct {
	tag  string
	idx  int
	hash string
}

func toValidScope(scope Scope) cement.Result[ValidScope] {
	if len(scope.Name) == 0 {
		return cement.Err[ValidScope]("Scope name is empty")
	}
	if reName.MatchString(scope.Name) {
		return cement.Err[ValidScope](fmt.Sprintf("Scope %v contains []", scope.Name))
	}
	scope.Tags = removeDuplicates(scope.Tags)
	hashed := make([]tagIdxHash, len(scope.Tags))
	for idx, tag := range scope.Tags {
		hash := hexHash(tag)
		hashed[idx] = tagIdxHash{tag, idx, hash}
	}
	sort.Slice(hashed, func(i, j int) bool {
		return hashed[i].hash < hashed[j].hash
	})
	tags := make([]string, len(hashed))
	for idx, tih := range hashed {
		tags[idx] = tih.tag
	}
	testScopeHash := getScopeHash(tags)
	if testScopeHash != scope.Checksum {
		return cement.Err[ValidScope](fmt.Sprintf("Checksum failed: %s != %s", testScopeHash, scope.Checksum))
	}
	// sort.Slice(hashed, func(i, j int) bool {
	// 	return hashed[i].idx < hashed[j].idx
	// })
	tag2index := make(map[string]int, len(hashed))
	for _, tih := range hashed {
		tags[tih.idx] = tih.tag
		tag2index[tih.tag] = tih.idx
	}
	return cement.Ok(ValidScope{
		Name:      scope.Name,
		Checksum:  scope.Checksum,
		Tag2index: tag2index,
		Tags:      tags,
	})
}

func Scopes2ValidScopes(scopes []Scope) cement.Result[[]ValidScope] {
	ret := make([]ValidScope, 0, len(scopes))
	// no map out of error handling
	for _, scope := range scopes {
		rvsc := toValidScope(scope)
		if rvsc.IsErr() {
			return cement.Err[[]ValidScope](rvsc.Err())
		}
		ret = append(ret, rvsc.Ok())
	}
	return cement.Ok(ret)
}

type Implosion struct {
	scopes []ValidScope
}

func NewImplosion(ip ImplosionParams) cement.Result[Implosion] {
	vsc := Scopes2ValidScopes(ip.Scopes)
	if vsc.IsErr() {
		return cement.Err[Implosion](vsc.Err())
	}
	return cement.Ok(Implosion{scopes: vsc.Ok()})
}

// func filter[T any](s []T, cond func(t T) bool) []T {
// 	res := []T{}
// 	for _, v := range s {
// 		if cond(v) {
// 			res = append(res, v)
// 		}
// 	}
// 	return res
// }

func (i Implosion) ToBits(tags []ScopedTags) cement.Result[string] {
	out := []string{}
	for _, st := range tags {
		var scope *ValidScope
		for _, scp := range i.scopes {
			if scp.Name == st.Name {
				scope = &scp
				break
			}
		}
		if scope == nil {
			return cement.Err[string](fmt.Sprintf("Scope not found: %v", st.Name))
		}
		idxBits := make([]int, 0, len(st.Tags))
		maxBit := -1
		for _, tag := range st.Tags {
			idx, ok := scope.Tag2index[tag]
			if !ok {
				return cement.Err[string](fmt.Sprintf("Tag not found in Scope: %v: %v", st.Name, tag))
			}
			idxBits = append(idxBits, idx) // the first bit need to be encoded
			if idx > maxBit {
				maxBit = idx
			}
		}
		if maxBit == -1 {
			continue
		}
		bits := make([]uint8, (maxBit+8)/8)
		for _, idx := range idxBits {
			i := idx / 8
			bits[i] = bits[i] | (1 << (idx % 8))
			// console.log("idx=", i, idx, idxBits, (1 << (idx % 8)), bits)
		}
		// console.log(bits, uarray2bits(bits), maxBit, idxBits)
		out = append(out, fmt.Sprintf("%s[%s]", scope.Name, uarray2bits(bits)))
	}
	return cement.Ok(strings.Join(out, ""))
}

func (i Implosion) FromBits(str string) cement.Result[[]ScopedTags] {
	rawParts := strings.Split(str, "]")
	parts := make([]string, 0, len(rawParts))
	for _, part := range rawParts {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		parts = append(parts, part)
	}
	out := make([]ScopedTags, 0, len(parts))
	for _, part := range parts {
		parts = strings.Split(part, "[")
		name, bitStr := parts[0], parts[1]
		bits, e := base58.Decode(bitStr)
		if e != nil {
			return cement.Err[[]ScopedTags](e.Error())
		}
		var scope *ValidScope
		for _, s := range i.scopes {
			if s.Name == name {
				scope = &s
				break
			}
		}
		if scope == nil {
			return cement.Err[[]ScopedTags](fmt.Sprintf("Scope not found: %v", name))
		}
		tags := make([]string, 0, len(bits)*8)
		for i := 0; i < len(bits); i++ {
			for j := 0; j < 8; j++ {
				if bits[i]&(1<<j) != 0 {
					tags = append(tags, scope.Tags[i*8+j])
				}
			}
		}
		out = append(out, ScopedTags{
			Name: name,
			Tags: tags,
		})
	}
	return cement.Ok(out)
}
