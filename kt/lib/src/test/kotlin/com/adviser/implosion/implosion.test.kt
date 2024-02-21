package com.adviser.implosion

import kotlin.test.Test
import kotlin.test.assertEquals

data class InBitVect(val inAsBits: String)

data class OutBitVect(val outAsBits: String)

data class TestCases(
    val _case: String,
    val input: List<ScopedTags>,
    val output: String,
    val from: List<ScopedTags>?
)

fun ensureOutBitVect(inb: InBitVect): OutBitVect {
  return OutBitVect(
      outAsBits =
          getImplosion()
              .fromBits(inb.inAsBits)
              .getOrThrow()
              .map { i -> "${i.name}[${i.tags.joinToString(",")}]" }
              .joinToString("")
  )
}

fun allOnes(n: Int): ByteArray {
  val ret = ByteArray(n)
  for (i in 0 until ret.size) {
    ret[i] = 0xff.toByte()
  }
  return ret
}

fun get1000(): List<String> {
  val o = ArrayList<String>()
  for (i in 0 until 1000) {
    o.add(i.toString())
  }
  return o
}

fun getImplosion(): Implosion {
  val o = get1000()
  return (Implosion.create(
          ImplosionParams(
              scopes =
                  listOf(
                      Scope(
                          name = "test",
                          checksum = getScopeHash(o),
                          tags = o,
                      ),
                      Scope(
                          name = "xtest",
                          checksum = "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
                          tags = listOf("c", "a", "b"),
                      ),
                  ),
          )
      ))
      .getOrThrow()
}

fun ensureInBitVect(inb: List<String>): InBitVect {
  return InBitVect(
      inAsBits = getImplosion().toBits(
                  listOf(
                      ScopedTags(
                          name = "test",
                          tags = inb,
                      ),
                  )
              )
              .getOrThrow()
  )
}

class ImplosionTest {

  @Test
  fun testHexHash() {
    val hash = hexHash("hello")
    assertEquals(hash, "42TEXg1vFAbcJ65y7qdYG9iCPvYfy3NDdVLd75akX2P5")
  }

  @Test
  fun testGetScopeHash() {
    assertEquals(getScopeHash(listOf("a", "b")), "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM")
    assertEquals(getScopeHash(listOf("b", "a")), "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM")
    assertEquals(
        getScopeHash(listOf("b", "a", "c")),
        "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58"
    )
  }

  @Test
  fun testRemoveDuplicates() {
    assertEquals(removeDuplicates(listOf("d", "a", "b", "a", "c")), listOf("d", "a", "b", "c"))
  }

  @Test
  fun testImplosionEmpty() {
    val implosion =
        Implosion.create(
            ImplosionParams(
                scopes = emptyList(),
            )
        )
    assertEquals(implosion.isSuccess, true)
  }

  @Test
  fun testImplosionDefectChecksum() {
    val implosion =
        Implosion.create(
            ImplosionParams(
                scopes =
                    listOf(
                        Scope(
                            name = "test",
                            checksum = "ab19ec537f09499b26f",
                            tags = listOf("a", "b"),
                        )
                    ),
            )
        )
    assertEquals(implosion.isFailure, true)
  }

  @Test
  fun TestImplosionFineName() {
    val implosion =
        Implosion.create(
            ImplosionParams(
                scopes =
                    listOf(
                        Scope(
                            name = "test😇",
                            checksum = "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
                            tags = listOf("a", "b"),
                        ),
                    ),
            )
        )
    assertEquals(implosion.isFailure, false)
  }

  @Test
  fun TestImplosionFineNameContainsOpenCorners() {
    val implosion =
        Implosion.create(
            ImplosionParams(
                scopes =
                    listOf(
                        Scope(
                            name = "test[😇",
                            checksum = "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
                            tags = listOf("a", "b"),
                        ),
                    ),
            )
        )
    assertEquals(implosion.isFailure, true)
  }

  @Test
  fun TestImplosionFineNameContainsCloseCorners() {
    val implosion =
        Implosion.create(
            ImplosionParams(
                scopes =
                    listOf(
                        Scope(
                            name = "test]😇",
                            checksum = "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
                            tags = listOf("a", "b"),
                        ),
                    ),
            )
        )
    assertEquals(implosion.isFailure, true)
  }

  @Test
  fun testImplosionChecksum() {
    val rimplosion =
        Implosion.create(
            ImplosionParams(
                scopes =
                    listOf(
                        Scope(
                            name = "test",
                            checksum = "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
                            tags = listOf("a", "b"),
                        ),
                        Scope(
                            name = "xtest",
                            checksum = "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
                            tags = listOf("c", "a", "b"),
                        ),
                    ),
            )
        )
    assertEquals(rimplosion.isSuccess, true)
    val implosion = rimplosion.getOrThrow()
    assertEquals(
        implosion.scopes,
        listOf(
            ValidScope(
                checksum = "2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM",
                name = "test",
                tag2index =
                    mapOf(
                        "b" to 1,
                        "a" to 0,
                    ),
                tags = listOf("a", "b"),
            ),
            ValidScope(
                checksum = "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
                name = "xtest",
                tag2index =
                    mapOf(
                        "a" to 1,
                        "b" to 2,
                        "c" to 0,
                    ),
                tags = listOf("c", "a", "b"),
            ),
        )
    )
  }

//   [ValidScope(
//     name=test,
//     checksum=2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM,
//     tag2index={a=1, b=0},
//     tags=[b, a]),
//       [ValidScope(
//         name=test,
//         checksum=2HGWGNKVpyBAqxPboi5rSY5rStbRtUrfUWrnQwTzF3gM,
//         tag2index={b=1, a=0},
//          tags=[a, b]),

//     ValidScope(
//         name=xtest,
//          checksum=2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58,
//          tag2index={a=1, b=2, c=0},
//           tags=[c, a, b])
//         ]

//         ValidScope(
//             name=xtest,
//             checksum=2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58,
//              tag2index={a=2, b=1, c=0},
//               tags=[c, b, a])
//               ]


  @Test
  fun TestRemoveDuplicatesFromTags() {
    val res =
        Implosion.create(
            ImplosionParams(
                scopes =
                    listOf(
                        Scope(
                            name = "xtest",
                            checksum = "2icyXAVNHz29D1dTVYE59sm5foRZmqqBTY26bZdN3q58",
                            tags = listOf("c", "a", "b", "a", "c"),
                        ),
                    ),
            )
        )
    assertEquals(res.isSuccess, true)

  }

  @Test
  fun TestImplosionToBitsScopeNotFound() {
    val implosion = getImplosion()
    val bits =
        implosion.toBits(
            listOf(
                ScopedTags(
                    name = "x",
                    tags = listOf("a"),
                ),
            )
        )
    assertEquals(bits.isFailure, true)
  }

  @Test
  fun testImplosionToBits() {
    for (testSet in
        listOf(
            TestCases(
                _case = "empty",
                input =
                    listOf(
                        ScopedTags(
                            name = "xtest",
                            tags = emptyList(),
                        ),
                    ),
                output = "",
                from = emptyList(),
            ),
            TestCases(
                _case = "single",
                input =
                    listOf(
                        ScopedTags(
                            name = "xtest",
                            tags = listOf("a"),
                        ),
                    ),
                output = "xtest[3]",
                from = null,
            ),
            TestCases(
                _case = "double",
                input =
                    listOf(
                        ScopedTags(
                            name = "xtest",
                            tags = listOf("a", "b"),
                        ),
                    ),
                output = "xtest[7]",
                from = null,
            ),
            TestCases(
                _case = "big",
                input =
                    listOf(
                        ScopedTags(
                            name = "test",
                            tags = get1000(),
                        ),
                    ),
                output = "test[${uarray2bits(allOnes(1000 / 8))}]",
                from = null,
            ),
            TestCases(
                _case = "mixed",
                input =
                    listOf(
                        ScopedTags(
                            name = "xtest",
                            tags = listOf("a", "b"),
                        ),
                        ScopedTags(
                            name = "test",
                            tags = get1000(),
                        ),
                    ),
                output =
                    "xtest[7]test[${uarray2bits(allOnes(1000 / 8))}]",
                from = null,
            ),
        )) {
      val implosion = getImplosion()
      assertEquals(implosion.toBits(testSet.input).getOrThrow(), testSet.output)

      val f = implosion.fromBits(testSet.output).getOrThrow()
      if (testSet.from != null) {
        assertEquals(f, testSet.from)
      } else {
        assertEquals(f, testSet.input)
      }
    }
  }

  @Test
  fun testEnsureOutBitVect() {
    assertEquals(
        ensureOutBitVect(
            InBitVect(
                inAsBits =
                    "test[111111111111111111B9uiJ2H3McB6kJoyaRsiWrSQoJYf8w9rgdX4pKhgbzvN2WDF6xnhLDJYcWP5QfeCjYbzAXN5j8C1KUasDtxdkxuULnG9WVi9QKYJDF]",
            )
        ),
        OutBitVect(
            outAsBits = "test[147,737]",
        )
    )
  }

  @Test
  fun testEnsureInBitVect() {
    assertEquals(
        ensureInBitVect(listOf("147", "737")),
        InBitVect(
            inAsBits =
                "test[111111111111111111B9uiJ2H3McB6kJoyaRsiWrSQoJYf8w9rgdX4pKhgbzvN2WDF6xnhLDJYcWP5QfeCjYbzAXN5j8C1KUasDtxdkxuULnG9WVi9QKYJDF]",
        )
    )
  }
}
