import stainless.annotation._
import stainless.collection._
import stainless.equations._
import stainless.lang._
import stainless.proof.check
import scala.annotation.tailrec
import scala.collection.immutable

case class ListMapLongKey[B](toList: List[(Long, B)]) {
  require(TupleListOps.isStrictlySorted(toList))

  @inline
  def isEmpty: Boolean = toList.isEmpty
  @inline
  def head: (Long, B) = {
    require(!isEmpty)
    toList.head
  }
  @inline
  def tail: ListMapLongKey[B] = {
    require(!isEmpty)
    ListMapLongKey(toList.tail)
  }

  @inline
  def contains(key: Long): Boolean = {
    val res = TupleListOps.containsKey(toList, key)
    if(res){
      TupleListOps.lemmaContainsKeyImpliesGetValueByKeyDefined(toList, key)
    }
    res

  }.ensuring(res => !res || this.get(key).isDefined)

  @inline
  def get(key: Long): Option[B] = {
    TupleListOps.getValueByKey(toList, key)
  }
  @inline
  def keysOf(value: B): List[Long] = {
    TupleListOps.getKeysOf(toList, value)
  }
  @inline
  def apply(key: Long): B = {
    require(contains(key))
    get(key).get
  }

  @inline
  def +(keyValue: (Long, B)): ListMapLongKey[B] = {

    val newList = TupleListOps.insertStrictlySorted(toList, keyValue._1, keyValue._2)

    TupleListOps.lemmaContainsTupThenGetReturnValue(newList, keyValue._1, keyValue._2)
    ListMapLongKey(newList)

  }.ensuring(res => res.contains(keyValue._1) && res.get(keyValue._1) == Some(keyValue._2))

  @inlineOnce
  def ++(keyValues: List[(Long, B)]): ListMapLongKey[B] = {
    decreases(keyValues)
    keyValues match {
      case Nil()                => this
      case Cons(keyValue, rest) => (this + keyValue) ++ rest
    }
  }
  @inlineOnce
  def -(key: Long): ListMapLongKey[B] = {
    ListMapLongKey(TupleListOps.removeStrictlySorted(toList, key))
  }.ensuring(res => !res.contains(key))

  @inlineOnce
  def --(keys: List[Long]): ListMapLongKey[B] = keys match {
    case Nil()           => this
    case Cons(key, rest) => (this - key) -- rest
  }
  @inline
  def forall(p: ((Long, B)) => Boolean): Boolean = {
    toList.forall(p)
  }
}

object TupleListOps {

  @inline
  def invariantList[B](l: List[(Long, B)]): Boolean = {
    isStrictlySorted(l)
  }

  def getKeysOf[B](l: List[(Long, B)], value: B): List[Long] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case head :: tl if (head._2 == value) => {
        if (!getKeysOf(tl, value).isEmpty) {
          lemmaForallGetValueByKeySameWithASmallerHead(tl, getKeysOf(tl, value), value, head)
        }
        head._1 :: getKeysOf(tl, value)
      }
      case head :: tl if (head._2 != value) => {
        val r = getKeysOf(tl, value)
        if (!getKeysOf(tl, value).isEmpty) {
          lemmaForallGetValueByKeySameWithASmallerHead(tl, getKeysOf(tl, value), value, head)
        }
        getKeysOf(tl, value)
      }
      case Nil() => Nil[Long]()
    }

  }.ensuring(res => res.forall(getValueByKey(l, _) == Some(value)))

  def filterByValue[B](l: List[(Long, B)], value: B): List[(Long, B)] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case head :: tl if (head._2 == value) => head :: filterByValue(tl, value)
      case head :: tl if (head._2 != value) => filterByValue(tl, value)
      case Nil()                            => Nil[(Long, B)]()
    }
  }.ensuring(res =>
    invariantList(res) && res.forall(_._2 == value) &&
      (if (l.isEmpty) res.isEmpty else res.isEmpty || res.head._1 >= l.head._1)
  )

  def getValueByKey[B](l: List[(Long, B)], key: Long): Option[B] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case head :: tl if (head._1 == key) => Some(head._2)
      case head :: tl if (head._1 != key) => getValueByKey(tl, key)
      case Nil()                          => None[B]
    }

  }

  def insertStrictlySorted[B](l: List[(Long, B)], newKey: Long, newValue: B): List[(Long, B)] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case head :: tl if (head._1 < newKey)  => head :: insertStrictlySorted(tl, newKey, newValue)
      case head :: tl if (head._1 == newKey) => (newKey, newValue) :: tl
      case head :: tl if (head._1 > newKey)  => (newKey, newValue) :: head :: tl
      case Nil()                             => (newKey, newValue) :: Nil()
    }
  }.ensuring(res => invariantList(res) && containsKey(res, newKey) && res.contains((newKey, newValue)))

  def removeStrictlySorted[B](l: List[(Long, B)], key: Long): List[(Long, B)] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case head :: tl if (head._1 == key) => tl
      case head :: tl if (head._1 != key) => head :: removeStrictlySorted(tl, key)
      case Nil()                          => Nil[(Long, B)]()
    }
  }.ensuring(res => invariantList(res) && !containsKey(res, key))

  @inlineOnce
  def isStrictlySorted[B](l: List[(Long, B)]): Boolean = {
    decreases(l)
    l match {
      case Nil()                                     => true
      case Cons(_, Nil())                            => true
      case Cons(h1, Cons(h2, _)) if (h1._1 >= h2._1) => false
      case Cons(_, t)                                => isStrictlySorted(t)
    }
  }

  def containsKey[B](l: List[(Long, B)], key: Long): Boolean = {
    require(invariantList(l))
    decreases(l)
    l match {
      case head :: tl if(head._1 == key) => true
      case head :: tl if(head._1 > key) => false
      case head :: tl if(head._1 < key) => containsKey(tl, key)
      case Nil() => false

    }
  }

  // ----------- LEMMAS -----------------------------------------------------

  def lemmaGetValueByKeyIsDefinedImpliesContainsKey[B](l: List[(Long, B)], key: Long): Unit = {
    require(invariantList(l) && getValueByKey(l, key).isDefined)
    l match {
      case head :: tl if(head._1 != key) => lemmaGetValueByKeyIsDefinedImpliesContainsKey(tl, key)
      case _ => ()
    }
  }.ensuring(_ => containsKey(l, key))

  def lemmaContainsKeyImpliesGetValueByKeyDefined[B](l: List[(Long, B)], key: Long): Unit = {
    require(invariantList(l) && containsKey(l, key))
    l match {
      case head :: tl if(head._1 != key) => lemmaContainsKeyImpliesGetValueByKeyDefined(tl, key)
      case _ => ()
    }
  }.ensuring(_ => getValueByKey(l, key).isDefined)

  def lemmaForallGetValueByKeySameWithASmallerHead[B](l: List[(Long, B)], keys: List[Long], value: B, newHead: (Long, B)): Unit = {
    require(invariantList(l) && !l.isEmpty && keys.forall(getValueByKey(l, _) == Some(value)) && newHead._1 < l.head._1)
    decreases(keys)

    keys match {
      case head :: tl => {
        lemmaGetValueByKeyIsDefinedImpliesContainsKey(l, head)
        lemmaContainsKeyImpliesGetValueByKeyDefined(newHead :: l, head)
        lemmaForallGetValueByKeySameWithASmallerHead(l, tl, value, newHead)
      }
      case _     => ()
    }

  }.ensuring(_ => keys.forall(k => getValueByKey(newHead :: l, k) == Some(value)))

  def lemmaInsertStrictlySortedDoesNotModifyOtherKeyValues[B](l: List[(Long, B)], newKey: Long, newValue: B, otherKey: Long): Unit = {
    require(invariantList(l) && newKey != otherKey)
    decreases(l)

    l match {
      case head :: tl if (head._1 != otherKey) => lemmaInsertStrictlySortedDoesNotModifyOtherKeyValues(tl, newKey, newValue, otherKey)
      case _                                   => ()
    }

  }.ensuring(_ => containsKey(insertStrictlySorted(l, newKey, newValue), otherKey) == containsKey(l, otherKey) &&
                  getValueByKey(insertStrictlySorted(l, newKey, newValue), otherKey) == getValueByKey(l, otherKey) )

  def lemmaInsertStrictlySortedDoesNotModifyOtherKeysNotContained[B](l: List[(Long, B)], newKey: Long, newValue: B, otherKey: Long): Unit = {
    require(invariantList(l) && !containsKey(l, otherKey) && otherKey != newKey)
    decreases(l)

    l match {
      case head :: tl => lemmaInsertStrictlySortedDoesNotModifyOtherKeysNotContained(tl, newKey, newValue, otherKey)
      case _          => ()
    }
  }.ensuring(_ => !containsKey(insertStrictlySorted(l, newKey, newValue), otherKey))

  def lemmaInsertStrictlySortedDoesNotModifyOtherKeysContained[B](l: List[(Long, B)], newKey: Long, newValue: B, otherKey: Long): Unit = {
    require(invariantList(l) && containsKey(l, otherKey) && otherKey != newKey)
    decreases(l)

    l match {
      case head :: tl if (head._1 != otherKey) => lemmaInsertStrictlySortedDoesNotModifyOtherKeysContained(tl, newKey, newValue, otherKey)
      case _                                   => ()
    }
  }.ensuring(_ => containsKey(insertStrictlySorted(l, newKey, newValue), otherKey))

  def lemmaNotContainsKeyThenNotContainsTuple[B](@induct l: List[(Long, B)], key: Long, value: B): Unit = {
    require(invariantList(l) && !containsKey(l, key))

  }.ensuring(_ => !l.contains((key, value)))

  def lemmaContainsTupleThenContainsKey[B](l: List[(Long, B)], key: Long, value: B): Unit = {
    require(invariantList(l) && l.contains((key, value)))
    decreases(l)

    l match {
      case head :: tl if (head != (key, value)) => lemmaContainsTupleThenContainsKey(tl, key, value)
      case _                                    => ()
    }
  }.ensuring(_ => containsKey(l, key))

  def lemmaContainsTupThenGetReturnValue[B](l: List[(Long, B)], key: Long, value: B): Unit = {
    require(invariantList(l) && containsKey(l, key) && l.contains((key, value)))
    decreases(l)

    l match {
      case head :: Nil()                  => ()
      case head :: tl if (head._1 == key) => lemmaNotContainsKeyThenNotContainsTuple(tl, key, value)
      case head :: tl => lemmaContainsTupThenGetReturnValue(tl, key, value)
      case Nil() => ()
    }
  }.ensuring(_ => getValueByKey(l, key) == Some(value))
}

object ListMapLongKey {
  def empty[B]: ListMapLongKey[B] = ListMapLongKey[B](List.empty[(Long, B)])
}

object ListMapLongKeyLemmas {
  import ListSpecs._

  @opaque
  def addValidProp[B](lm: ListMapLongKey[B], p: ((Long, B)) => Boolean, a: Long, b: B): Unit = {
    require(lm.forall(p) && p(a, b))
    decreases(lm.toList.size)

    if (!lm.isEmpty)
      addValidProp(lm.tail, p, a, b)

  }.ensuring { _ =>
    val nlm = lm + (a, b)
    nlm.forall(p)
  }

  @opaque
  def removeValidProp[B](lm: ListMapLongKey[B], p: ((Long, B)) => Boolean, a: Long): Unit = {
    require(lm.forall(p))

    if (!lm.isEmpty)
      removeValidProp(lm.tail, p, a)

  }.ensuring { _ =>
    val nlm = lm - a
    nlm.forall(p)
  }

  @opaque
  def insertAllValidProp[B](lm: ListMapLongKey[B], kvs: List[(Long, B)], p: ((Long, B)) => Boolean): Unit = {
    require(lm.forall(p) && kvs.forall(p))
    decreases(kvs)

    if (!kvs.isEmpty) {
      addValidProp(lm, p, kvs.head._1, kvs.head._2)
      insertAllValidProp(lm + kvs.head, kvs.tail, p)
    }

  }.ensuring { _ =>
    val nlm = lm ++ kvs
    nlm.forall(p)
  }

  @opaque
  def removeAllValidProp[B](lm: ListMapLongKey[B], l: List[Long], p: ((Long, B)) => Boolean): Unit = {
    require(lm.forall(p))
    decreases(l)

    if (!l.isEmpty) {
      removeValidProp(lm, p, l.head)
      removeAllValidProp(lm - l.head, l.tail, p)
    }

  }.ensuring { _ =>
    val nlm = lm -- l
    nlm.forall(p)
  }

  @opaque
  def addApplyDifferent[B](lm: ListMapLongKey[B], a: Long, b: B, a0: Long): Unit = {
    require(lm.contains(a0) && a0 != a)
    assert(TupleListOps.containsKey(lm.toList, a0))
    TupleListOps.lemmaInsertStrictlySortedDoesNotModifyOtherKeyValues(lm.toList, a, b, a0)
    TupleListOps.lemmaContainsKeyImpliesGetValueByKeyDefined(lm.toList, a0)

  }.ensuring(_ => (lm + (a -> b))(a0) == lm(a0))

  @opaque
  def addStillContains[B](lm: ListMapLongKey[B], a: Long, b: B, a0: Long): Unit = {
    require(lm.contains(a0))

    if (a != a0)
      TupleListOps.lemmaInsertStrictlySortedDoesNotModifyOtherKeysContained(lm.toList, a, b, a0)

  }.ensuring(_ => (lm + (a, b)).contains(a0))

  @opaque
  def addStillNotContains[B](lm: ListMapLongKey[B], a: Long, b: B, a0: Long): Unit = {
    require(!lm.contains(a0) && a != a0)

    TupleListOps.lemmaInsertStrictlySortedDoesNotModifyOtherKeysNotContained(lm.toList, a, b, a0)

  }.ensuring(_ => !(lm + (a, b)).contains(a0))

  @opaque
  def applyForall[B](lm: ListMapLongKey[B], p: ((Long, B)) => Boolean, k: Long): Unit = {
    require(lm.forall(p) && lm.contains(k))
    decreases(lm.toList.size)

    if (!lm.isEmpty && lm.toList.head._1 != k)
      applyForall(lm.tail, p, k)

  }.ensuring(_ => p(k, lm(k)))

  @opaque
  def getForall[B](lm: ListMapLongKey[B], p: ((Long, B)) => Boolean, k: Long): Unit = {
    require(lm.forall(p))
    decreases(lm.toList.size)

    if (!lm.isEmpty && lm.toList.head._1 != k)
      getForall(lm.tail, p, k)

  }.ensuring(_ => lm.get(k).forall(v => p(k, v)))

  @opaque
  def uniqueImage[B](lm: ListMapLongKey[B], a: Long, b: B): Unit = {
    require(lm.toList.contains((a, b)))

    TupleListOps.lemmaContainsTupleThenContainsKey(lm.toList, a, b)
    TupleListOps.lemmaContainsTupThenGetReturnValue(lm.toList, a, b)

  }.ensuring(_ => lm.get(a) == Some[B](b))

  @opaque
  def keysOfSound[B](lm: ListMapLongKey[B], value: B): Unit = {
    //trivial by postcondition of getKeysOf
  }.ensuring(_ => lm.keysOf(value).forall(key => lm.get(key) == Some[B](value)))
}
