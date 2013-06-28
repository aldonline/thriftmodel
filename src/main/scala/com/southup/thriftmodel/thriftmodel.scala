package com.southup.thriftmodel

import scala.util.matching.Regex
import scala.collection.mutable.{ ArrayBuffer => MArrayBuffer, Queue => MQueue, HashMap => MHashMap }

trait Node {
  val write: String
  protected class Joinable( x: Traversable[Node] ) {
    def join( sep: String ) = x.map( _.write ).mkString( sep )
  }
  protected implicit def wrapAsJoinable( x: Traversable[Node] ) = new Joinable( x )
}

sealed class FieldRequirementType { def write = "" }
object FieldRequired extends FieldRequirementType { override def write = "required" }
object FieldOptional extends FieldRequirementType { override def write = "optional" }
object FieldNormal extends FieldRequirementType

abstract class Value
abstract class Literal extends Value
case class StringLiteral( val lexical: String ) extends Literal
case class NumericLiteral( val lexical: String ) extends Literal
case class Sym( val id: String ) extends Value

case class Type(
    val id: String,
    val params: Seq[Type] = Nil ) extends Node {
  lazy val mentionsTypeIds: Set[String] = ( List( id ) ++ params.flatMap( _.mentionsTypeIds ) ).toSet
  lazy val write: String = id + ( params.nonEmpty match {
    case false => ""
    case true  => "<" + params.join( "," ) + ">"
  } )
}

object Type {
  val VOID = Type( "void" )
}

case class Method(
    val id: String,
    val fields: Seq[Field] = Nil,
    val tpe: Type = Type.VOID,
    val throws: Seq[Field] = Nil,
    val oneway: Boolean = false ) extends Node {
  private def writeArguments = "( " + fields.join( " , " ) + " )"
  private def writeExceptions = throws.isEmpty match {
    case true  => ""
    case false => " throws ( " + throws.join( " , " ) + " )"
  }
  lazy val write = ( if ( oneway ) "oneway " else "" ) + tpe.write + " " + id + writeArguments + writeExceptions
}

case class Service(
    id: String,
    extds: Seq[Service] = Nil,
    methods: Seq[Method] = Nil ) extends Node {
  lazy val write = "service " + id + "{\n  " + methods.join( ",\n\n  " ) + "\n}"
}

case class Struct( // ( and exceptions )
    id: String,
    fields: Seq[Field],
    isException: Boolean = false ) extends Node {
  lazy val dependsOnTypeIds: Set[String] = fields.flatMap( _.mentionsTypeIds ).toSet
  lazy val write = ( if ( isException ) "exception" else "struct" ) + " " + id + " {\n " + fields.join( ";\n " ) + "\n}"
}

case class Field(
    val id: String,
    val tpe: Type,
    val index: Int,
    val defaultValue: Option[Value] = None,
    val requirement: FieldRequirementType = FieldNormal ) extends Node {
  if ( index < 1 )
    throw new IllegalArgumentException( "Field indexes in Thrift must be positive integers greater than zero." +
      "You specified: " + index )
  lazy val mentionsTypeIds = tpe.mentionsTypeIds
  lazy val write =
    index + ":" + ( if ( requirement.write.isEmpty ) "" else requirement.write + " " ) + tpe.write + " " + id // TODO: default value
}

case class TypeDef(
    id: String,
    tpe: Type ) extends Node {
  lazy val write = "typedef " + tpe.write + " " + id
}

case class Enum( id: String, members: Seq[EnumMember] ) extends Node {
  lazy val write = "enum " + id + " {\n" + members.join( ",\n" ) + "\n}"
}

case class EnumMember( id: String, num: Int ) extends Node {
  lazy val write = id + " = " + num
}

case class Const(
    val id: String,
    val tpe: Type,
    val value: Value ) extends Node {
  lazy val write = "const " + tpe.write + " " + id + " = " + 5 // TODO: write literal values
}

case class Api(
    val typedefs: Seq[TypeDef] = Nil,
    val enums: Seq[Enum] = Nil,
    val constants: Seq[Const] = Nil,
    val structs: Seq[Struct] = Nil,
    val exceptions: Seq[Struct] = Nil,
    val services: Seq[Service] = Nil ) {

  def getStructById( id: String ) = structs.find( _.id == id )

  lazy val structsSortedByDependency = {
    val structs = MQueue[Struct]( this.structs: _* )
    val result = MArrayBuffer[Struct]()
    var i: Int = 0
    def iter() {
      if ( i > 10000 )
        throw new Error( "Cyclic struct dependencies are not supported by Thrift." +
          "see: http://grokbase.com/t/thrift/user/0984cqwxen/recursive-datatypes" )
      i = i + 1
      if ( structs.nonEmpty ) {
        val struct = structs.dequeue
        resolveStructDependencies( struct ).subsetOf( result.toSet ) match {
          case true  => result += struct
          case false => structs.enqueue( struct )
        }
        iter()
      }
    }
    iter()
    result.toSeq
  }

  private val cache = new MHashMap[Struct, Set[Struct]]
  def resolveStructDependencies( struct: Struct ): Set[Struct] =
    cache.getOrElseUpdate( struct, struct.fields.flatMap( _.mentionsTypeIds ).distinct.flatMap( getStructById ).toSet )

  def write( prefix: Option[String] = None ) = {
    def title( str: String ) = "\n#-----------------------\n#    " + str + "\n#-----------------------\n"
    def w( t: Seq[{ def write: String }], s: String = "\n" ) = t.map( _.write ) mkString s
    val doc = List(
      w( typedefs ),
      w( constants ),
      w( enums ),
      w( exceptions ),
      w( structsSortedByDependency ),
      w( services )
    ).mkString( "\n\n" )
    prefix match {
      case None => doc
      case Some( prefix ) => {
        println( "will add prefix " + prefix )
        // lets replace all enum, exception and struct IDs for a prefixed version
        // TODO: prefix typedefs as well?
        val ids = ( enums.map( _.id ) ++ exceptions.map( _.id ) ++ structs.map( _.id ) ).toSet
        IdentifierReplacer.addPrefixToIdentifiers( ids, prefix, doc )
      }
    }
  }
}

private object IdentifierReplacer {
  def addPrefixToIdentifiers( identifiers: Set[String], prefix: String, doc: String ) =
    identifiers.foldLeft( doc )( ( d, id ) => replaceAll( id, prefix + id, d ) )

  private def regexCache = new MHashMap[String, Regex]

  private def getRegex( x: String ) = regexCache.getOrElseUpdate( x, ( "([^0-9a-zA-Z]+)(" + x + ")([^0-9a-zA-Z]+)" ).r )

  private def replaceOne( id1: String, id2: String, source: String ): Option[( String, String )] =
    getRegex( id1 ).findFirstMatchIn( source ) map { mtch =>
        val head   = source.substring( 0, mtch.start )
        val middle = mtch.matched.replace( id1, id2 )
        val rest   = source.substring( mtch.end, source.length )
        ( head + middle, rest )
    }

  private def replaceAll( id1: String, id2: String, source: String ) = {
    def iter( t: ( String, String ) ): String = replaceOne( id1, id2, t._2 ) match {
      case None       => t._1 + t._2
      case Some( t2 ) => iter( ( t._1 + t2._1, t2._2 ) )
    }
    iter( "", source )
  }
}

