package lt.tabo.json2s

import java.io.InputStreamReader

import org.json4s.JsonAST._
import org.json4s.native._
import treehugger.forest._
import treehugger.forest.definitions._
import treehugger.forest.treehuggerDSL._
import org.json4s.DefaultFormats
import scala.util.matching.Regex
import lt.tabo.json2s.Utils.{canBeDate, toUpperCamel, toSingular}

case class JsonToScala(json: JValue, className: String) {
  def asJson = prettyJson(renderJValue(json))
  def asScala = {
    import JsonToScala._
    treesToString(classFor(json, className)._1)
  }
}

object JsonToScala {
  def classFor(value: JValue, paramName: String): (Seq[Tree], Type) = value match {
    case JString(s) =>
      if (canBeDate(s)) (Nil, "java.util.Date")
      else (Nil, StringClass)
    case i: JInt => (Nil, IntClass)
    case d: JDouble => (Nil, DoubleClass)
    case b: JBool => (Nil, BooleanClass)
    case o: JObject => generateClassFromJObject(o, toUpperCamel(paramName))
    case a: JArray => classForJArray(a, paramName)
    case x => throw new Error("Don't know how to handle " + x)
  }

  def classForJArray(json: JArray, paramName: String): (Seq[Tree], Type) = {
    val arr = json.arr
    def terminal(t: Type) = (Nil: Seq[Tree], t)
    val (trees: Seq[Tree], arrType: Type) =
      if (arr.isEmpty) terminal(NothingClass)
      else if (arr.forall(_.isInstanceOf[JString])) {
        if (arr.forall(js => canBeDate(js.asInstanceOf[JString].s))) terminal("java.util.Date")
        else terminal(StringClass)
      }
      else if (arr.forall(_.isInstanceOf[JInt])) terminal(IntClass)
      else if (arr.forall(_.isInstanceOf[JDouble])) terminal(DoubleClass)
      else if (arr.forall(_.isInstanceOf[JBool])) terminal(BooleanClass)
      else if (arr.forall(_.isInstanceOf[JObject]))
        generateClassFromJObjects(arr.map(_.asInstanceOf[JObject]), toUpperCamel(toSingular(paramName)))
      else if (arr.forall(_.isInstanceOf[JArray]))
        // not safe - assume all arrays are of the same type, just use the first one
        classForJArray(arr.head.asInstanceOf[JArray], paramName)
      else throw new Error("Array types are not all the same in " + json)
    (trees, TYPE_LIST(arrType))
  }

  def getParamsForJObject(json: JObject): (Seq[Tree], Seq[(String, Type)]) = {
    val (moreClasses: Seq[Tree], params: Seq[(String,Type)]) = ((Seq[Tree](),Seq[(String,Type)]()) /: json.obj.toList) {
      case ((treesSoFar, valsSoFar), (name: String, value)) =>
        val (classDefs: Seq[Tree],thisClass: Type) = classFor(value, name)
        (treesSoFar ++ classDefs, valsSoFar :+ (name, thisClass))
    }
    (moreClasses, params)
  }

  def generateClassFromJObjects(jsons: List[JObject], className: String): (Seq[Tree], Type) = {
    val TopCaseClass = RootClass.newClass(className)

    val objectParams: List[(Seq[Tree], Seq[(String, Type, Boolean)])] =
      jsons.map(getParamsForJObject).map { case (trees, ps) =>
        (trees, ps.map { case (name, classType) => (name, classType, false) })
      }
    val (moreClasses, params) = objectParams.reduce((x,y) => (x,y) match {
      case ((someClasses1, someParams1), (someClasses2, someParams2)) =>
        // someClasses1 union someClasses2 - hack since .equals is not the same as the treeToString
        // TODO: a recursive merge
        val mergeClasses = (someClasses1 ++ someClasses2).groupBy(treeToString(_)).map(_._2.head)
        // the boolean means "optional"
        val (optParams1, reqPs1) = someParams1.partition(_._3)
        val (optParams2, reqPs2) = someParams2.partition(_._3)
        val mergeOpts = (optParams1 ++ optParams2).groupBy(_._1).mapValues(_.head).toList
        val mergeReqs = {
          (reqPs1 ++ reqPs2).groupBy(_._1).mapValues {
            case Seq(one) => one.copy(_3 = true)
            case Seq(one, two) => one
            case xs => xs.head
          }
        }
        val mergeParams = mergeOpts ++ mergeReqs
        (mergeClasses, mergeParams.map(_._2))
    })

    val newClass: Tree = CASECLASSDEF(TopCaseClass).withParams(params.toIterable.map {
      case (name, classType, optional) =>
        PARAM(Utils.quotedName(name), if (optional) TYPE_OPTION(classType) else classType).empty
    })

    ((moreClasses :+ newClass).toSeq, className)
  }

  def generateClassFromJObject(json: JObject, className: String): (Seq[Tree], Type) = {
    generateClassFromJObjects(List(json), className)
  }

  def treesToString(trees: Iterable[Tree]) = {
    treeToString(BLOCK(trees).withoutPackage)
  }

  def classForExamples(jsons: Seq[String], className: String): String = {
    treesToString {
      generateClassFromJObjects(jsons.toList.map(JsonParser.parse(_) match {
        case obj: JObject => obj
        case x => throw new IllegalArgumentException("Expected JObject, got " + x)
      }), className)._1
    }
  }

  def apply(json: String, className: String): JsonToScala = {
    apply(JsonParser.parse(json), className)
  }

  def demo() = {
    val youtubeResponse = getClass.getResourceAsStream("youtube.json")
    val jObject = JsonParser.parse(new InputStreamReader(youtubeResponse)) match {
      case obj: JObject => obj
      case x => throw new Error("Expected JObject, got " + x)
    }

    println(apply(jObject, "YouTubeResponse"))
  }

  def main(args: Array[String]) = demo()
}
