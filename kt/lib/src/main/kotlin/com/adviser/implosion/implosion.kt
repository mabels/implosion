package com.adviser.implosion

import foundation.metaplex.base58.decodeBase58
import foundation.metaplex.base58.encodeToBase58String
import java.security.MessageDigest

data class Scope(
    val name: String,
    val checksum: String,
    val tags: List<String>,
)

data class ImplosionParams(val scopes: List<Scope>)

data class ScopedTags(val name: String, val tags: List<String>)

data class ValidScope(
    val name: String,
    val checksum: String,
    val tag2index: Map<String, Int>,
    val tags: List<String>, // ordered
)

fun uarray2bits(uarray: ByteArray): String {
  return uarray.encodeToBase58String()
}

fun hexHash(s: String): String {
  val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
  return uarray2bits(bytes)
}

fun getScopeHash(tags: List<String>): String {
  // be careful of duplicate tags
  val hashed = tags.map { hexHash(it) }.sorted()
  return hexHash(hashed.joinToString(""))
}

fun removeDuplicates(arr: List<String>): List<String> {
  return arr.toSet().toList()
}

data class TagIdxHash(val tag: String, val idx: Int, val hash: String)

fun toValidScope(iscope: Scope): Result<ValidScope> {
  if (iscope.name == "") {
    return Result.failure(NoSuchElementException("Scope name is empty"))
  }
  if (Regex("""[\[\]]""").containsMatchIn(iscope.name)) {
    return Result.failure(NoSuchElementException("Scope ${iscope.name} contains []"))
  }
  val scope =
      Scope(name = iscope.name, checksum = iscope.checksum, tags = removeDuplicates(iscope.tags))
  val hashed =
      scope.tags
          .mapIndexed { idx: Int, v: String -> TagIdxHash(tag = v, idx = idx, hash = hexHash(v)) }
          .sortedBy { selector -> selector.hash }

  val testScopeHash = getScopeHash(hashed.map { i -> i.tag })
  if (testScopeHash != scope.checksum) {
    return Result.failure(
        NoSuchElementException("Checksum failed: ${testScopeHash} != ${scope.checksum}")
    )
  }
  val tag2index = HashMap<String, Int>()
  val tags = ArrayList<String>(hashed.size)
  for (i in 0 until hashed.size) {
    tags.add("")
  }
  for (i in 0 until hashed.size) {
    tag2index[hashed[i].tag] = hashed[i].idx
    tags.set(hashed[i].idx, hashed[i].tag)
  }
  return Result.success(
      ValidScope(
          name = scope.name,
          checksum = scope.checksum,
          tag2index = tag2index,
          tags = tags,
      )
  )
}

public fun Scopes2ValidScopes(scopes: List<Scope>): Result<List<ValidScope>> {
  val ret = ArrayList<ValidScope>()
  // no map out of error handling
  for (scope in scopes) {
    val rvsc = toValidScope(scope)
    if (rvsc.isFailure) {
      // return rvsc as unknown as Result<ValidScope[]>;
      return Result.failure(rvsc.exceptionOrNull() as Throwable)
    }
    ret.add(rvsc.getOrThrow())
  }
  return Result.success(ret)
}

class Implosion(scopes: List<ValidScope>) {
  val scopes = scopes

  companion object {
    fun create(ip: ImplosionParams): Result<Implosion> {
      val vsc = Scopes2ValidScopes(ip.scopes)
      if (vsc.isFailure) return Result.failure(vsc.exceptionOrNull() as Throwable)
      return Result.success(Implosion(vsc.getOrThrow()))
    }
  }

  fun toBits(tags: List<ScopedTags>): Result<String> {
    val out = ArrayList<String>()
    for (st in tags) {
      val scope = scopes.find { it.name == st.name }
      if (scope == null) {
        return Result.failure(NoSuchElementException("Scope not found: $st.name"))
      }
      val idxBits = ArrayList<Int>()
      var maxBit = -1
      for (tag in st.tags) {
        val idx = scope.tag2index[tag]
        if (idx == null) {
          return Result.failure(NoSuchElementException("Tag not found in Scope: $st.name:$tag"))
        }
        idxBits.add(idx) // the first bit need to be encoded
        maxBit = Math.max(maxBit, idx)
      }
      if (maxBit == -1) {
        continue
      }
      val bits = ByteArray((maxBit + 8) / 8)
      for (idx in idxBits) {
        val i = (idx / 8);
        bits.set(i, (bits.get(i).toInt() or (1 shl (idx % 8))).toByte());
      }
      out.add("${scope.name}[${uarray2bits(bits)}]")
    }
    return Result.success(out.joinToString(""))
  }

  fun fromBits(str: String): Result<List<ScopedTags>> {
    val parts = str.split("]").map { it.trim() }.filter({ it != "" })
    val out = ArrayList<ScopedTags>()
    for (part in parts) {
      val splitted = part.split("[")
      val name = splitted[0]
      val bitStr = splitted[1]
      val bits = bitStr.decodeBase58()
      val scope = scopes.find { it.name == name }
      if (scope == null) {
        return Result.failure(NoSuchElementException("Scope not found: $name"))
      }
      val tags = ArrayList<String>()
      for (i in 0 until bits.size) {
        for (j in 0 until 8) {
          if ((bits.get(i).toInt() and (1 shl j)) != 0) {
            tags.add(scope.tags.get(i * 8 + j))
          }
        }
      }
      out.add(ScopedTags(name = name, tags = tags))
    }
    return Result.success(out)
  }
}
