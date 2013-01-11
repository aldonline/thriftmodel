
http://svn.apache.org/repos/asf/thrift/trunk/tutorial/tutorial.thrift

    // typedef i32 MyInteger
    val typedef1 = TypeDef(
        id = "MyInteger",
        tpe = Type( "i32" )
    )  
    
    // const i32 INT32CONSTANT = 9853
    val const1 = Const(
        id = "INT32CONSTANT",
        tpe = Type("i32"),
        value = NumericLiteral("9853")
    )

    val mapstrstr = Type(
        id = "map",
        params = Seq(Type("string"), Type("string"))
    )

    // const map<string,string> MAPCONSTANT = {'hello':'world', 'goodnight':'moon'}
    val const2 = Const(
        id = "MAPCONSTANT",
        tpe = mapstrstr,
        value = NumericLiteral( "5" ) // TODO: expression
    )
    
    /*
    enum Operation {
      ADD = 1,
      SUBTRACT = 2,
      MULTIPLY = 3,
      DIVIDE = 4
    }
    */
    val enum1 = Enum(
      id = "Operation",
      members = List(
        EnumMember( "ADD", 1 ),
        EnumMember( "SUBTRACT", 2 ),
        EnumMember( "MULTIPLY", 3 ),
        EnumMember( "DIVIDE", 4 )         
      )
    )
    
    /*
    struct Work {
      1: i32 num1 = 0,
      2: i32 num2,
      3: Operation op,
      4: optional string comment,
    }
    */
    
    val struct1 = Struct(
      id = "Work",
      fields = Seq(
        Field(
          id = "num1",
          tpe = Type( "i32" ),
          index = 1,
          defaultValue = Some( NumericLiteral( "0" ) )
        ),
        Field(
          id = "num2",
          tpe = Type( "i32" ),
          index = 2
        ),
        Field(
          id = "op",
          tpe = Type( "Operation" ),
          index = 3,
          defaultValue = Some( Sym("Operation.ADD") )
        ),
        Field(
          id = "comment",
          tpe = Type( "string" ),
          index = 4,
          requirement = FieldOptional
        )
      )
    )
    
    val recursiveStruct = Struct(
      id = "Person",
      fields = Seq( /* Field( id="friend", tpe=Type("Person"), index=1 )*/ ) // <-- if you enable this the generated thrift will not be valid and will break when serializing
    )
    
    
    /*
    exception InvalidOperation {
      1: i32 what,
      2: string why
    }
    */

    val excp1 = Struct(
      id = "InvalidOperation",
      fields = Seq(
        Field(
          id = "what",
          tpe = Type( "i32" ),
          index = 1
        ),
        Field(
          id = "why",
          tpe = Type( "string" ),
          index = 2
        )
      ),
      isException = true
    )
    
    
    
    val method1 = Method(
      id = "ping",
      tpe = Type("void")
    )
    
    val method2 = Method(
      id = "add",
      tpe = Type("i32"),
      fields = Seq(
        Field(
          id = "num1",
          tpe = Type("i32"),
          index = 1
        ), 
        Field(
          id = "num2",
          tpe = Type("i32"),
          index = 2
        )
      )
    )
    
    val method3 = Method(
      id = "calculate",
      tpe = Type("i32"),
      fields = Seq(
        Field( id="logid", tpe=Type("i32"), index=1 ),
        Field( id="w", tpe=Type("Work"), index=2 )
      ),
      throws = Seq(
        Field( id="ouch", tpe=Type("InvalidOperation"), index=1 )    
      )
    )
    
    val method4 = Method(
      id = "zip",
      oneway = true 
    )
    
    
    val service1 = Service(
      id = "Calculator",
      extds = Seq(),
      methods = Seq( method1, method2, method3, method4 )
    )
    
    // service Calculator extends shared.SharedService {
    
    //   void ping(),
    
    //   i32 add(1:i32 num1, 2:i32 num2),
    
    //   i32 calculate(1:i32 logid, 2:Work w) throws (1:InvalidOperation ouch),
    
    //   oneway void zip()
        
    val api = Api(
      services = List(service1),
      constants = List(const1, const2),
      typedefs = List(typedef1),
      enums = List(enum1),
      structs = List(struct1, recursiveStruct),
      exceptions = List(excp1)
    )
