package implosion

import (
	"fmt"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestHexHash(t *testing.T) {
	hash := hexHash("hello")
	assert.Equal(t, hash, "42TEXg1vFAbcJ65y7qdYG9iCPvYfy3NDdVLd75akX2P5")
}

func TestGetScopeHash(t *testing.T) {
	assert.Equal(t, getScopeHash([]string{"a", "b"}), "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM")
	assert.Equal(t, getScopeHash([]string{"b", "a"}), "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM")
	assert.Equal(t, getScopeHash([]string{"b", "a", "c"}), "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58")
}

func TestRemoveDuplicates(t *testing.T) {
	assert.Equal(t, removeDuplicates([]string{"d", "a", "b", "a", "c"}), []string{"d", "a", "b", "c"})

}

func TestImplosionempty(t *testing.T) {
	implosion := NewImplosion(ImplosionParams{
		Scopes: []Scope{},
	})
	assert.Equal(t, implosion.IsOk(), true)
}

func TestImplosionDefectChecksum(t *testing.T) {
	implosion := NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "test",
				Checksum: "ab19ec537f09499b26f",
				Tags:     []string{"a", "b"},
			},
		},
	})
	assert.Equal(t, implosion.IsErr(), true)
}

func TestImplosionFineName(t *testing.T) {
	implosion := NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "test😇",
				Checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
				Tags:     []string{"a", "b"},
			},
		},
	})
	assert.Equal(t, implosion.IsErr(), false)
}

func TestImplosionFineNameContains(t *testing.T) {
	implosion := NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "test[😇",
				Checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
				Tags:     []string{"a", "b"},
			},
		},
	})
	assert.Equal(t, implosion.IsErr(), true)
}

func TestImplosionFineNameContainsCorners(t *testing.T) {
	implosion := NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "test]😇",
				Checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
				Tags:     []string{"a", "b"},
			},
		},
	})
	assert.Equal(t, implosion.IsErr(), true)
}

func TestImplosionChecksum(t *testing.T) {
	rimplosion := NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "test",
				Checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
				Tags:     []string{"a", "b"},
			},
			{
				Name:     "xtest",
				Checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
				Tags:     []string{"c", "a", "b"},
			},
		},
	})
	assert.Equal(t, rimplosion.IsOk(), true)
	implosion := rimplosion.Ok()
	assert.Equal(t, implosion.scopes, []ValidScope{
		{
			Checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
			Name:     "test",
			Tag2index: map[string]int{
				"b": 1,
				"a": 0,
			},
			Tags: []string{"a", "b"},
		},
		{
			Checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
			Name:     "xtest",
			Tag2index: map[string]int{
				"a": 1,
				"b": 2,
				"c": 0,
			},
			Tags: []string{"c", "a", "b"},
		},
	})
}

func get1000Tags() []string {
	o := make([]string, 1000)
	for idx := range o {
		o[idx] = fmt.Sprintf("%d", idx)
	}
	return o
}
func getImplosion() Implosion {
	o := get1000Tags()
	return NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "test",
				Checksum: getScopeHash(o),
				Tags:     o,
			},
			{
				Name:     "xtest",
				Checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
				Tags:     []string{"c", "a", "b"},
			},
		},
	}).Ok()
}

func TestRemoveDuplicatesFromTags(t *testing.T) {
	res := NewImplosion(ImplosionParams{
		Scopes: []Scope{
			{
				Name:     "xtest",
				Checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
				Tags:     []string{"c", "a", "b", "a", "c"},
			},
		},
	})
	assert.Equal(t, res.IsOk(), true)
}

func TestImplosionToBitsScopeNotFound(t *testing.T) {
	implosion := getImplosion()
	bits := implosion.ToBits([]ScopedTags{
		{
			Name: "x",
			Tags: []string{"a"},
		},
	})
	assert.Equal(t, bits.IsErr(), true)
}

type tests struct {
	_case  string
	input  []ScopedTags
	output string
	from   []ScopedTags
}

func TestImplosionToBits(t *testing.T) {
	test := make([]byte, 1000/8)
	for idx := range test {
		test[idx] = 0xff
	}
	for _, testSet := range []tests{
		{
			_case: "empty",
			input: []ScopedTags{
				{
					Name: "xtest",
					Tags: []string{},
				},
			},
			output: "",
			from:   []ScopedTags{},
		},
		{
			_case: "single",
			input: []ScopedTags{
				{
					Name: "xtest",
					Tags: []string{"a"},
				},
			},
			output: "xtest[3]",
		},
		{
			_case: "double",
			input: []ScopedTags{
				{
					Name: "xtest",
					Tags: []string{"a", "b"},
				},
			},
			output: "xtest[7]",
		},
		{
			_case: "big",
			input: []ScopedTags{
				{
					Name: "test",
					Tags: get1000Tags(),
				},
			},
			output: fmt.Sprintf("test[%s]", uarray2bits(test)),
		},
		{
			_case: "mixed",
			input: []ScopedTags{
				{
					Name: "xtest",
					Tags: []string{"a", "b"},
				},
				{
					Name: "test",
					Tags: get1000Tags(),
				},
			},
			output: fmt.Sprintf("xtest[7]test[%s]", uarray2bits(test)),
		},
	} {
		implosion := getImplosion()
		assert.Equal(t, implosion.ToBits(testSet.input).Ok(), testSet.output)

		f := implosion.FromBits(testSet.output).Ok()
		if testSet.from != nil {
			assert.Equal(t, f, testSet.from)
		} else {
			assert.Equal(t, f, testSet.input)
		}
	}
}

type InBitVect struct {
	inAsBits string
}

type OutBitVect struct {
	outAsBits string
}

func ensureOutBitVect(inb InBitVect) OutBitVect {
	vs := getImplosion().FromBits(inb.inAsBits).Ok()
	strs := make([]string, len(vs))
	for idx, v := range vs {
		strs[idx] = fmt.Sprintf("%s[%s]", v.Name, strings.Join(v.Tags, ","))
	}
	return OutBitVect{
		outAsBits: strings.Join(strs, ""),
	}
}

func TestEnsureOutBitVect(t *testing.T) {
	assert.Equal(t,
		ensureOutBitVect(InBitVect{
			inAsBits: "test[111111111111111111B9uiJ2H3McB6kJoyaRsiWrSQoJYf8w9rgdX4pKhgbzvN2WDF6xnhLDJYcWP5QfeCjYbzAXN5j8C1KUasDtxdkxuULnG9WVi9QKYJDF]",
		}), OutBitVect{
			outAsBits: "test[147,737]",
		})
}

func ensureInBitVect(inb []string) InBitVect {
	return InBitVect{
		inAsBits: getImplosion().ToBits([]ScopedTags{
			{
				Name: "test",
				Tags: inb,
			},
		}).Ok(),
	}
}

func TestEnsureInBitVect(t *testing.T) {
	assert.Equal(t, ensureInBitVect([]string{"147", "737"}), InBitVect{
		inAsBits: "test[111111111111111111B9uiJ2H3McB6kJoyaRsiWrSQoJYf8w9rgdX4pKhgbzvN2WDF6xnhLDJYcWP5QfeCjYbzAXN5j8C1KUasDtxdkxuULnG9WVi9QKYJDF]",
	})
}
