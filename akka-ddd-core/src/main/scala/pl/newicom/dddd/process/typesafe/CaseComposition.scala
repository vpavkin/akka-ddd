package pl.newicom.dddd.process.typesafe

import shapeless._

trait FindCase[P, H <: HList, A <: HList] {
  type Result
  def get(h: H): poly.Case.Aux[P, A, Result]
}

object FindCase extends LowerPriorityFindCase {
  type Aux[P, H <: HList, A <: HList, Result0] = FindCase[P, H, A] {
    type Result = Result0
  }
  implicit def hcons[P, M <: HList, MResult, T <: HList]: FindCase.Aux[P, poly.Case.Aux[P, M, MResult] :: T, M, MResult] = new FindCase[P, poly.Case.Aux[P, M, MResult] :: T, M] {
    override type Result = MResult
    override def get(h: poly.Case.Aux[P, M, MResult] :: T): poly.Case.Aux[P, M, Result] = h.head
  }
}
trait LowerPriorityFindCase {
  implicit def hcons2[P, H, M <: HList, TResult, T <: HList](implicit f: FindCase.Aux[P, T, M, TResult]): FindCase.Aux[P, H :: T, M, TResult] = new FindCase[P, H :: T, M] {
    override type Result = TResult
    override def get(h: H :: T): poly.Case.Aux[P, M, Result] = f.get(h.tail)
  }
}

sealed trait CaseList[P, +C <: HList] {
  def value: C
}

trait CaseComposition extends LowerPriorirtyCaseComposition {
  def cases[C <: HList](c: C) = new CaseList[this.type, C] {
    override def value: C = c
  }

  implicit def hcons[A <: HList, AOut, T <: HList](implicit hc: CaseList[this.type, poly.Case.Aux[this.type, A, AOut] :: T]): poly.Case.Aux[this.type, A, AOut] = hc.value.head
}
trait LowerPriorirtyCaseComposition {
  implicit def hcons2[H, A <: HList, AOut, T <: HList](implicit t: CaseList[this.type, H :: T], findCase: FindCase.Aux[this.type, T, A, AOut]): poly.Case.Aux[this.type, A, AOut] = findCase.get(t.value.tail)
}
