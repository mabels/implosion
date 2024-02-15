import { Result } from "@adviser/cement";
import bs58 from "bs58";

export interface Scope {
    readonly name: string;
    readonly checksum: string;
    readonly tags: string[];
}

export interface ImplosionParams {
    readonly scopes: Scope[]
}

export interface ScopedTags {
    readonly name: string;
    readonly tags: string[];
}

export interface ValidScope {
    readonly name: string;
    readonly checksum: string;
    readonly tag2index: Record<string, number>;
    readonly tags: string[]; // ordered
}

export function uarray2bits(uarray: Uint8Array): string {
    return bs58.encode(uarray);
}

const encoder = new TextEncoder();
export async function hexHash(s: string): Promise<string> {
    const hbuf = await crypto.subtle.digest("SHA-256", encoder.encode(s));
    const hashArray = new Uint8Array(hbuf); // convert buffer to byte array
    return uarray2bits(hashArray);
}

export async function getScopeHash(tags: string[]): Promise<string> {
    const hashed = (await Promise.all(tags.map((tag) => hexHash(tag)))).sort();
    return await hexHash(hashed.join(""));
}

async function toValidScope(scope: Scope): Promise<Result<ValidScope>> {
    if (scope.name === "") {
        return Result.Err("Scope name is empty")
    }
    if (/[[\]]/.test(scope.name)) {
        return Result.Err(`Scope ${scope.name} contains []`)
    }
    const hashed = (await Promise.all(scope.tags.map((tag, idx) => new Promise<{
        readonly tag: string,
        readonly idx: number,
        readonly hash: string
    }>((resolve) => {
        hexHash(tag).then((hash) => {
            resolve({ tag, idx, hash })
        })
    })))).sort((a, b) => a.hash.localeCompare(b.hash))
    const testScopeHash = await getScopeHash(hashed.map(i => i.tag));
    if (testScopeHash !== scope.checksum) {
        return Result.Err(`Checksum failed: ${testScopeHash} !== ${scope.checksum}`)
    }
    return Result.Ok({
        name: scope.name,
        checksum: scope.checksum,
        tag2index: hashed.reduce((acc, h) => {
            acc[h.tag] = h.idx
            return acc
        }, {} as Record<string, number>),
        tags: hashed.sort((a,b)=>(a.idx-b.idx)).map((h) => h.tag),
    })
}

async function Scopes2ValidScopes(scopes: Scope[]): Promise<Result<ValidScope[]>> {
    const ret: ValidScope[] = []
    // no map out of error handling
    for (const scope of scopes) {
        const rvsc = await toValidScope(scope)
        if (rvsc.isErr()) {
            return rvsc as unknown as Result<ValidScope[]>;
        }
        ret.push(rvsc.Ok())
    }
    return Result.Ok(ret)
}

export class Implosion {
    public static async create(ip: ImplosionParams) {
        const vsc = await Scopes2ValidScopes(ip.scopes);
        if (vsc.isErr()) return vsc as unknown as Result<Implosion>;
        return Result.Ok(new Implosion(vsc.Ok()))
    }
    readonly scopes: ValidScope[];
    private constructor(vsc: ValidScope[]) {
        this.scopes = vsc
    }

    toBits(tags: ScopedTags[]): Result<string> {
        const out: string[] = []
        for (const st of tags) {
            const scope = this.scopes.find((s) => s.name === st.name);
            if (scope === undefined) {
                return Result.Err(`Scope not found: ${st.name}`)
            }
            const idxBits: number[] = []
            let maxBit = -1;
            for (const tag of st.tags) {
                const idx = scope.tag2index[tag];
                if (idx === undefined) {
                    return Result.Err(`Tag not found in Scope: ${st.name}:${tag}`)
                }
                idxBits.push(idx); // the first bit need to be encoded
                maxBit = Math.max(maxBit, idx);
            }
            if (maxBit === -1) {
                continue;
            }
            const bits = new Uint8Array((maxBit + 8) / 8);
            for (const idx of idxBits) {
                const i = ~~(idx / 8);
                bits[i] = bits[i] | (1 << (idx % 8));
                // console.log("idx=", i, idx, idxBits, (1 << (idx % 8)), bits)
            }
            // console.log(bits, uarray2bits(bits), maxBit, idxBits)
            out.push(`${scope.name}[${uarray2bits(bits)}]`)
        }
        return Result.Ok(out.join(""))
    }

    fromBits(str: string): Result<ScopedTags[]> {
        const parts = str.split(']').map((s) => s.trim()).filter((s) => s);
        const out: ScopedTags[] = []
        for (const part of parts) {
            const [name, bitStr] = part.split('[')
            const bits = bs58.decode(bitStr);
            const scope = this.scopes.find((s) => s.name === name);
            if (scope === undefined) {
                return Result.Err(`Scope not found: ${name}`)
            }
            const tags: string[] = []
            for (let i = 0; i < bits.length; i++) {
                for (let j = 0; j < 8; j++) {
                    if (bits[i] & (1 << j)) {
                        tags.push(scope.tags[i*8+j]);
                    }
                }
            }
            out.push({ name, tags })
        }
        return Result.Ok(out)
    }
}
