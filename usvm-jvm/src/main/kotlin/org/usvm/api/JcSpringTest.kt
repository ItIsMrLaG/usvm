package org.usvm.api

import org.usvm.machine.state.JcState


import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.toType
import org.usvm.*
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.machine.state.concreteMemory.JcConcreteMemory
import org.usvm.util.name


fun JcClasspath.findJcMethod(cName: String, mName: String): JcTypedMethod {
    val method = this.findClass(cName).toType().findMethodOrNull { it.name == mName }
    method?.let { return it }
    throw MethodNotFoundException("$mName not found")
}

fun JcClasspath.stringType(): JcType =
    this.findClassOrNull("java.lang.String")?.toType() ?: error("No string type in classpath")

fun JcClasspath.intType(): JcType =
    this.findClassOrNull("java.lang.Integer")?.toType() ?: error("No integer type in classpath")

fun List<String>.toStringArrayDsl(cp: JcClasspath): Pair<UTestCreateArrayExpression, MutableList<UTestInst>> {
    val initDSL = mutableListOf<UTestInst>()
    val stringType = cp.stringType()
    val intType = cp.intType()

    val arrayDSL = UTestCreateArrayExpression(
        elementType = stringType,
        size = UTestIntExpression(this.size, intType),
    ).also { initDSL.add(it) }

    this.forEachIndexed { idx, str ->
        UTestArraySetStatement(
            arrayInstance = arrayDSL,
            index = UTestIntExpression(idx, intType),
            setValueExpression = UTestStringExpression(str, stringType),
        ).also { initDSL.add(it) }
    }
    return Pair(arrayDSL, initDSL)
}

interface SpringReqAttr

data class ParamAttr(
    val name: String,
    val values: List<Any>,
//    val valueType: JcClassOrInterface, TODO: mb use it to generate DSL or concretize? (but it need support from Arthur)
) : SpringReqAttr

data class HeaderAttr(
    val name: String,
    val values: List<Any>,
//    val valueType: JcClassOrInterface, TODO: mb use it to generate DSL or concretize? (but it need support from Arthur)
) : SpringReqAttr

data class SpringReqPath(
    val name: String,
    val pathVariables: List<Any>
)

enum class SpringReqKind {
    GET,
    PUT,
    POST,
    PATCH,
    DELETE,
}

enum class SpringReqSettings {
    PATH,
    KIND,
}

data class SpringResponse(val statusCode: Int) {
//    TODO (?!?!?!?)
}

class SpringReqDSLBuilder private constructor(
    private val initStatements: MutableList<UTestInst>,
    private var reqDSL: UTestExpression,
    private val cp: JcClasspath
) {
    companion object {

        fun createReq(cp: JcClasspath, kind: SpringReqKind, path: SpringReqPath): SpringReqDSLBuilder = when (kind) {
            SpringReqKind.GET -> ::get
            SpringReqKind.PUT -> ::put
            SpringReqKind.POST -> ::post
            SpringReqKind.PATCH -> ::patch
            SpringReqKind.DELETE -> ::delete
        }(cp, path.name, path.pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CP =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private fun commonReqDSLBuilder(
            type: String,
            cp: JcClasspath,
            path: String,
            pathVariables: List<Any>
        ): SpringReqDSLBuilder {
            val staticMethod = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, type).method
            val pathDSL = UTestStringExpression(path, cp.stringType())
            val (arrayDSL, initDSL) = pathVariables.map { it.toString() }.toStringArrayDsl(cp)

            return SpringReqDSLBuilder(
                initStatements = initDSL,
                reqDSL = UTestStaticMethodCall(staticMethod, listOf(pathDSL, arrayDSL)),
                cp = cp
            )
        }

        // static org.springframework.test.web.servlet.request.MockMvcRequestBuilders#get
        fun get(cp: JcClasspath, path: String, pathVariables: List<Any>): SpringReqDSLBuilder =
            commonReqDSLBuilder("get", cp, path, pathVariables)

        // static org.springframework.test.web.servlet.request.MockMvcRequestBuilders#put
        fun put(cp: JcClasspath, path: String, pathVariables: List<Any>): SpringReqDSLBuilder =
            commonReqDSLBuilder("put", cp, path, pathVariables)

        // static org.springframework.test.web.servlet.request.MockMvcRequestBuilders#post
        fun post(cp: JcClasspath, path: String, pathVariables: List<Any>): SpringReqDSLBuilder =
            commonReqDSLBuilder("post", cp, path, pathVariables)

        // static org.springframework.test.web.servlet.request.MockMvcRequestBuilders#patch
        fun patch(cp: JcClasspath, path: String, pathVariables: List<Any>): SpringReqDSLBuilder =
            commonReqDSLBuilder("patch", cp, path, pathVariables)

        // static org.springframework.test.web.servlet.request.MockMvcRequestBuilders#delete
        fun delete(cp: JcClasspath, path: String, pathVariables: List<Any>): SpringReqDSLBuilder =
            commonReqDSLBuilder("delete", cp, path, pathVariables)
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getDSL() = reqDSL

    fun addParam(attr: ParamAttr): SpringReqDSLBuilder {
        val method = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, "param").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    fun addHeader(attr: HeaderAttr): SpringReqDSLBuilder {
        val method = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, "header").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    private fun addStrArrOfStrCallDSL(mName: JcMethod, str: String, arrOfStr: List<Any>) {
        val strArgDSL = UTestStringExpression(str, cp.stringType())
        val arrArgsDSL = arrOfStr.map { it.toString() }.toStringArrayDsl(cp).let { (argsDSL, initDSL) ->
            initStatements.addAll(initDSL)
            argsDSL
        }
        UTestMethodCall(
            instance = reqDSL,
            method = mName,
            args = listOf(strArgDSL, arrArgsDSL),
        ).also { reqDSL = it }
    }

    fun addAttrs(attrs: List<SpringReqAttr>): SpringReqDSLBuilder {
        attrs.forEach { attr ->
            when (attr) {
                is ParamAttr -> addParam(attr)
                is HeaderAttr -> addHeader(attr)
            }
        }
        return this
    }

    /*
     *
     * [ ] accept(String... mediaTypes)
     * [ ] accept(MediaType... mediaTypes)
     * [ ] characterEncoding(String encoding)
     * [ ] characterEncoding(Charset encoding)
     * [ ] content(byte[] content)
     * [ ] content(String content)
     * [ ] contentType(String contentType)
     * [ ] contentType(MediaType contentType)
     * [ ] contextPath(String contextPath)
     * [ ] cookie(Cookie... cookies)
     * [ ] flashAttr(String name, Object value)
     * [ ] flashAttrs(Map<String,Object> flashAttributes)
     * [ ] formField(String name, String... values)
     * [ ] formFields(MultiValueMap<String,String> formFields)
     * TODO: [ ] header(String name, Object... values)
     * [ ] headers(HttpHeaders httpHeaders)
     * [ ] locale(Locale locale)
     * [ ] locale(Locale... locales)
     * TODO: [ ] param(String name, String... values)
     * [ ] params(MultiValueMap<String,String> params)
     * [ ] pathInfo(String pathInfo)
     * [ ] principal(Principal principal)
     * [ ] queryParam(String name, String... values)
     * [ ] queryParams(MultiValueMap<String,String> params)
     * [ ] remoteAddress(String remoteAddress)
     * [ ] requestAttr(String name, Object value)
     * [ ] secure(boolean secure)
     * [ ] servletPath(String servletPath)
     * [ ] session(MockHttpSession session)
     * [ ] sessionAttr(String name, Object value)
     * [ ] sessionAttrs(Map<String,Object> sessionAttributes)
     * [ ] uri(String uriTemplate, Object... uriVariables)
     * [ ] uri(URI uri)
     * [ ] with(RequestPostProcessor postProcessor)
     * */

}

class SpringTestExecDSLBuilder private constructor(
    private val cp: JcClasspath,
    private val initStatements: MutableList<UTestInst>,
    private var mockMvcDSL: UTestExpression,
    private var isPerformed: Boolean = false,
) {
    companion object {
        /*
        * DSL STEPS:
        *   ctxManager: TestContextManager = new TestContextManager(<GENERATED-CLASS>.class)
        *   generatedClass: <GENERATED-CLASS> = new <GENERATED-CLASS>()
        *   ctxManager.prepareTestInstance(generatedClass)
        *   mockMvc: MockMvc = generatedClass.<FIELD-WITH-MOCKMVC>
        * */
        fun intiTestCtx(
            cp: JcClasspath,
            generatedTestClass: JcClassType,
            fromField: JcField
        ): SpringTestExecDSLBuilder {
            val initStatements = mutableListOf<UTestInst>()

            val testCtxManagerName = "org.springframework.test.context.TestContextManager"
            val testCtxManagerDSL = UTestConstructorCall(
                method = cp.findJcMethod(testCtxManagerName, "<init>").method,
                args = listOf(UTestClassExpression(generatedTestClass))
            ).also { initStatements.add(it) }

            val generatedClassInstDSL = UTestConstructorCall(
                method = cp.findJcMethod(generatedTestClass.name, "<init>").method,
                args = listOf()
            ).also { initStatements.add(it) }

            UTestMethodCall(
                instance = testCtxManagerDSL,
                method = cp.findJcMethod(testCtxManagerName, "prepareTestInstance").method,
                args = listOf(generatedClassInstDSL)
            ).also { initStatements.add(it) }

            val mockMvcDSL = UTestGetFieldExpression(
                instance = generatedClassInstDSL,
                field = fromField,
            ).also { initStatements.add(it) }

            return SpringTestExecDSLBuilder(
                cp = cp,
                initStatements = initStatements,
                mockMvcDSL = mockMvcDSL,
            )
        }
    }

    fun addPerformCall(reqDSL: UTestExpression): SpringTestExecDSLBuilder {
        UTestMethodCall(
            instance = mockMvcDSL,
            method = cp.findJcMethod("org.springframework.test.web.servlet.MockMvc", "perform").method,
            args = listOf(reqDSL)
        ).also {
            mockMvcDSL = it
            isPerformed = true
        }
        return this
    }

    fun addAndExpectCall(args: List<UTestExpression>): SpringTestExecDSLBuilder {
        assert(isPerformed)

        UTestMethodCall(
            instance = mockMvcDSL,
            method = cp.findJcMethod("org.springframework.test.web.servlet", "andExpect").method,
            args = args
        ).also {
            mockMvcDSL = it
        }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getExecDSL(): UTestCall {
        assert(isPerformed)
        return mockMvcDSL as UTestCall
    }
}

class SpringMatchersDSLBuilder(
    val cp: JcClasspath
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"

    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    fun addStatusCheck(int: Int): SpringMatchersDSLBuilder {
        val statusMatcherDSL = UTestStaticMethodCall(
            method = cp.findJcMethod(
                "$SPRING_RESULT_PACK.MockMvcResultMatchers",
                "status"
            ).method,
            args = listOf()
        ).also { initStatements.add(it) }

        val intDSL = UTestIntExpression(
            value = int,
            //TODO: original method StatusResultMatchers.is() takes a primitive type int, so this may be incorrect
            type = cp.intType()
        ).also { initStatements.add(it) }

        UTestMethodCall(
            instance = statusMatcherDSL,
            method = cp.findJcMethod("$SPRING_RESULT_PACK.StatusResultMatchers", "is").method,
            args = listOf(intDSL)
        ).also { matchers.add(it) }

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getMatchersDSL(): List<UTestExpression> = matchers
}

interface JcSpringTestDslGenerator {
    fun generateTestDSL(cp: JcClasspath): UTest
}

class JcExnSpringTest private constructor(
    /*TODO*/
) : JcSpringTestDslGenerator {

    companion object {
        fun generateFromState(state: JcState): JcExnSpringTest = JcExnSpringTest(/* TODO */)
    }

    override fun generateTestDSL(cp: JcClasspath): UTest {
        TODO("Not yet implemented")
    }
}

class JcResponseSpringTest private constructor(
    val generatedTestClass: JcClassType,
    /* Request information */
    val reqAttrs: List<SpringReqAttr>,
    val reqKind: SpringReqKind,
    val reqPath: SpringReqPath,
    /* Response information */
    val res: SpringResponse, // TODO!!!
) : JcSpringTestDslGenerator {
    companion object {
        fun generateFromState(state: JcState): JcResponseSpringTest = JcResponseSpringTest(
            getGeneratedClassName(state.ctx.cp),
            getReqAttrs(state),
            getReqKind(state),
            getReqPath(state),
            getSpringResponse(/*TODO: should it be state or something like UReadOnlyMemory<JcType>?*/)
        )

        private fun getGeneratedClassName(cp: JcClasspath): JcClassType {
            val cl = cp.findClassOrNull("StartSpringTestClass") //TODO: get it from state? (it is generated in runtime)
            assert(cl != null)
            return cl!!.toType()
        }

        private fun getReqKind(state: JcState): SpringReqKind {
            val kindValue = state.reqSetup[SpringReqSettings.KIND]?.let { it as UConcreteHeapRef }
            assert(kindValue != null)
            assert(kindValue?.address != null)

            val type = state.ctx.stringType as JcClassType
            // TODO: should I rewrite it with model usage?
            val kind = (state.memory as JcConcreteMemory).concretize(state, kindValue!!, kindValue, type) as String

            return when (kind) {
                "get" -> SpringReqKind.GET
                "put" -> SpringReqKind.PUT
                "post" -> SpringReqKind.POST
                "patch" -> SpringReqKind.PATCH
                "delete" -> SpringReqKind.DELETE
                else -> throw IllegalArgumentException("Unsupported kind: $kind")
            }
        }

        private fun getReqPath(state: JcState): SpringReqPath {
            val pathValue = state.reqSetup[SpringReqSettings.PATH]?.let { it as UConcreteHeapRef }
            assert(pathValue != null)
            assert(pathValue?.address != null)

            val type = state.ctx.stringType as JcClassType
            //TODO: should I rewrite it with model usage?
            val path = (state.memory as JcConcreteMemory).concretize(state, pathValue!!, pathValue, type) as String

            return SpringReqPath(
                name = path,
                pathVariables = listOf(/*TODO: GET PATH-PARAMS*/)
            )
        }

        private fun getReqAttrs(state: JcState): MutableList<SpringReqAttr> {
            fun getHeaderAttr(name: String, expr: UExpr<out USort>): HeaderAttr {
                val valueExpr = state.models[0].eval(expr) as UConcreteHeapRef

                if (valueExpr.address == NULL_ADDRESS) return HeaderAttr(
                    name = name,
                    values = listOf() // TODO: Is it true??? (don't understand how interpret NULL_ADDRESS)
                )

                // TODO: Is it really always a String??? (From where I can get JcType?) By my mind it should be an Array of objects?
                val type = state.ctx.stringType as JcClassType

                val concreteValue: List<Any>? =
                    (state.memory as JcConcreteMemory).concretize(state, valueExpr, valueExpr as UHeapRef, type)
                        ?.let { value ->
                            if (value is Iterable<*>) value.map { it!! }.toList()
                            else listOf(value)
                        }

                assert(concreteValue != null) //TODO: is it true???
                return HeaderAttr(
                    name = name,
                    values = concreteValue!!,
                )
            }

            fun getParamAttr(name: String, expr: UExpr<out USort>): ParamAttr {
                val valueExpr = state.models[0].eval(expr) as UConcreteHeapRef

                if (valueExpr.address == NULL_ADDRESS) return ParamAttr(
                    name = name,
                    values = listOf() // TODO: Is it true??? (don't understand how interpret NULL_ADDRESS)
                )

                // TODO: Is it really always a String??? (From where I can get JcType?) By my mind it should be an Array of objects?
                val type = state.ctx.stringType as JcClassType

                val concreteValue: List<Any>? =
                    (state.memory as JcConcreteMemory).concretize(state, valueExpr, valueExpr as UHeapRef, type)
                        ?.let { value ->
                            if (value is Iterable<*>) value.map { it!! }.toList()
                            else listOf(value)
                        }

                assert(concreteValue != null) //TODO: is it true???
                return ParamAttr(
                    name = name,
                    values = concreteValue!!,
                )
            }

            return state.userDefinedValues.toList().map { (key, expr) ->
                assert(key.contains("_"))
                val name = key.split("_").also { it.subList(1, it.size) }.joinToString("_")

                if (key.contains("PARAM_*".toRegex()))
                    getParamAttr(name, expr)

                if (key.contains("HEADER_*".toRegex()))
                    getHeaderAttr(name, expr)

                error("Unexpected key in userDefinedValues: $key")
            }.toMutableList()
        }

        private fun getSpringResponse(): SpringResponse {
            return SpringResponse(200)// TODO("Oh....")
        }
    }

    override fun generateTestDSL(cp: JcClasspath): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecDSLBuilder.intiTestCtx(
            cp = cp,
            generatedTestClass = generatedTestClass,
            fromField = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field //TODO: mb error here
        ).also { initStatements.addAll(it.getInitDSL()) }

        val reqDSL = generateReqDSL(cp, reqKind, reqPath, reqAttrs).let { (reqDSL, reqInitDSL) ->
            initStatements.addAll(reqInitDSL)
            reqDSL
        }
        testExecBuilder.addPerformCall(reqDSL)

        val matchersDSL = generateMatchersDSL(cp).let { (matchersDSL, matchersInitDSL) ->
            initStatements.addAll(matchersInitDSL)
            matchersDSL
        }
        matchersDSL.forEach { testExecBuilder.addAndExpectCall(listOf(it)) }

        return UTest(
            initStatements = initStatements,
            callMethodExpression = testExecBuilder.getExecDSL()
        )
    }

    private fun generateMatchersDSL(cp: JcClasspath): Pair<List<UTestExpression>, List<UTestInst>> {
        val matchersBuilder = SpringMatchersDSLBuilder(cp)

        matchersBuilder.addStatusCheck(res.statusCode)
//      TODO("add more matchers")

        return Pair(matchersBuilder.getMatchersDSL(), matchersBuilder.getInitDSL())
    }

    private fun generateReqDSL(
        cp: JcClasspath,
        reqKind: SpringReqKind,
        reqPath: SpringReqPath,
        reqAttrs: List<SpringReqAttr>
    ): Pair<UTestExpression, List<UTestInst>> {
        val builder = SpringReqDSLBuilder.createReq(cp, reqKind, reqPath).addAttrs(reqAttrs)
        return Pair(builder.getDSL(), builder.getInitDSL())
    }
}

fun createJcSpringTest(state: JcState): JcSpringTestDslGenerator {
    TODO("JcResponseSpringTest(...) or JcExnSpringTest(...)")
}
