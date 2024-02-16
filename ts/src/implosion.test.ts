import { Implosion, getScopeHash, hexHash, uarray2bits } from "./implosion";

it("hexHash", async () => {
  const hash = await hexHash("hello");
  expect(hash).toBe("42TEXg1vFAbcJ65y7qdYG9iCPvYfy3NDdVLd75akX2P5");
});

it("getScopeHash", async () => {
  expect(await getScopeHash(["a", "b"])).toBe("2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM");
  expect(await getScopeHash(["b", "a"])).toBe("2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM");
  expect(await getScopeHash(["b", "a", "c"])).toBe("2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58");
});

it("implosion empty", async () => {
  const implosion = await Implosion.create({
    scopes: [],
  });
  expect(implosion.isOk()).toBe(true);
});

it("implosion defect checksum", async () => {
  const implosion = await Implosion.create({
    scopes: [
      {
        name: "test",
        checksum: "ab19ec537f09499b26f",
        tags: ["a", "b"],
      },
    ],
  });
  expect(implosion.isErr()).toBe(true);
});

it("implosion fine name", async () => {
  const implosion = await Implosion.create({
    scopes: [
      {
        name: "test😇",
        checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
        tags: ["a", "b"],
      },
    ],
  });
  expect(implosion.isErr()).toBe(false);
});

it("implosion fine name contains ]", async () => {
  const implosion = await Implosion.create({
    scopes: [
      {
        name: "test[😇",
        checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
        tags: ["a", "b"],
      },
    ],
  });
  expect(implosion.isErr()).toBe(true);
});

it("implosion fine name contains [", async () => {
  const implosion = await Implosion.create({
    scopes: [
      {
        name: "test]😇",
        checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
        tags: ["a", "b"],
      },
    ],
  });
  expect(implosion.isErr()).toBe(true);
});

it("implosion checksum", async () => {
  const rimplosion = await Implosion.create({
    scopes: [
      {
        name: "test",
        checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
        tags: ["a", "b"],
      },
      {
        name: "xtest",
        checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
        tags: ["c", "a", "b"],
      },
    ],
  });
  expect(rimplosion.isOk()).toBe(true);
  const implosion = rimplosion.Ok();
  expect(implosion.scopes).toEqual([
    {
      checksum: "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
      name: "test",
      tag2index: {
        b: 1,
        a: 0,
      },
      tags: ["a", "b"],
    },
    {
      checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
      name: "xtest",
      tag2index: {
        a: 1,
        b: 2,
        c: 0,
      },
      tags: ["c", "a", "b"],
    },
  ]);
});

async function getImplosion(): Promise<Implosion> {
  const o = new Array(1000).fill(0).map((_, idx) => idx.toString());
  return (
    await Implosion.create({
      scopes: [
        {
          name: "test",
          checksum: await getScopeHash(o),
          tags: o,
        },
        {
          name: "xtest",
          checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
          tags: ["c", "a", "b"],
        },
      ],
    })
  ).Ok();
}

it("remove duplicates from tags", async () => {
  const res = await Implosion.create({
    scopes: [
      {
        name: "xtest",
        checksum: "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
        tags: ["c", "a", "b", "a", "c"],
      },
    ],
  });
  expect(res.isOk()).toBe(true);
});

it("implosion toBits scope not found", async () => {
  const implosion = await getImplosion();
  const bits = implosion.toBits([
    {
      name: "x",
      tags: ["a"],
    },
  ]);
  expect(bits.isErr()).toBe(true);
});

describe("implosion toBits", () => {
  for (const testSet of [
    {
      case: "empty",
      input: [
        {
          name: "xtest",
          tags: [],
        },
      ],
      output: "",
      from: [],
    },
    {
      case: "single",
      input: [
        {
          name: "xtest",
          tags: ["a"],
        },
      ],
      output: "xtest[3]",
    },
    {
      case: "double",
      input: [
        {
          name: "xtest",
          tags: ["a", "b"],
        },
      ],
      output: "xtest[7]",
    },
    {
      case: "big",
      input: [
        {
          name: "test",
          tags: new Array(1000).fill(0).map((_, idx) => idx.toString()),
        },
      ],
      output: `test[${uarray2bits(new Uint8Array(new Array(1000 / 8)).fill(0xff))}]`,
    },
    {
      case: "mixed",
      input: [
        {
          name: "xtest",
          tags: ["a", "b"],
        },
        {
          name: "test",
          tags: new Array(1000).fill(0).map((_, idx) => idx.toString()),
        },
      ],
      output: `xtest[7]test[${uarray2bits(new Uint8Array(new Array(1000 / 8)).fill(0xff))}]`,
    },
  ]) {
    it(`toBits for ${testSet.case}`, async () => {
      const implosion = await getImplosion();
      expect(implosion.toBits(testSet.input).Ok()).toEqual(testSet.output);
    });
    it(`fromBits for ${testSet.case}`, async () => {
      const implosion = await getImplosion();
      const f = implosion.fromBits(testSet.output).Ok();
      if (testSet.from) {
        expect(f).toEqual(testSet.from);
      } else {
        expect(f).toEqual(testSet.input);
      }
    });
  }
});

interface InBitVect {
  readonly inAsBits: string;
}

interface OutBitVect {
  readonly outAsBits: string;
}

async function ensureOutBitVect(inb: InBitVect): Promise<OutBitVect> {
  return {
    outAsBits: (await getImplosion())
      .fromBits(inb.inAsBits)
      .Ok()
      .map((i) => `${i.name}[${i.tags.join(",")}]`)
      .join(""),
  };
}

it("ensureOutBitVect", async () => {
  const a = new Uint8Array(200);
  a[19] = 1;
  expect(
    await ensureOutBitVect({
      inAsBits:
        "test[111111111111111111B9uiJ2H3McB6kJoyaRsiWrSQoJYf8w9rgdX4pKhgbzvN2WDF6xnhLDJYcWP5QfeCjYbzAXN5j8C1KUasDtxdkxuULnG9WVi9QKYJDF]",
    }),
  ).toEqual({
    outAsBits: "test[147,737]",
  });
});

async function ensureInBitVect(inb: string[]): Promise<InBitVect> {
  return {
    inAsBits: (await getImplosion())
      .toBits([
        {
          name: "test",
          tags: inb,
        },
      ])
      .Ok(),
  };
}

it("ensureInBitVect", async () => {
  expect(await ensureInBitVect(["147", "737"])).toEqual({
    inAsBits:
      "test[111111111111111111B9uiJ2H3McB6kJoyaRsiWrSQoJYf8w9rgdX4pKhgbzvN2WDF6xnhLDJYcWP5QfeCjYbzAXN5j8C1KUasDtxdkxuULnG9WVi9QKYJDF]",
  });
});
