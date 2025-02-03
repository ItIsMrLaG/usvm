package org.usvm.api

import org.usvm.machine.state.JcState


import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.toType
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.util.name


fun JcClasspath.findJcMethod(cName: String, mName: String): JcTypedMethod {
    val method = this.findClass(cName).toType().findMethodOrNull { it.name == mName }
    method?.let { return it }
    throw MethodNotFoundException("$mName not found")
}

interface SpringReqAttr

data class ParamAttr(
    val name: String,
    val values: List<Any>,
    val valueType: JcClassOrInterface,
) : SpringReqAttr

data class HeaderAttr(
    val name: String,
    val values: List<Any>,
    val valueType: JcClassOrInterface,
) : SpringReqAttr

data class SpringReqURI(
    val name: String,
    val uriVariables: List<Any>
)

enum class SpringReqType {
    GET,
    PUT,
    POST,
    PATCH,
    DELETE,
}

data class SpringResponse(val statusCode: Int) {
//    TODO (?!?!?!?)
}

class SpringReqDSLBuilder private constructor(
    private var reqDSL: UTestExpression,
) {
    companion object {

        fun createReq(type: SpringReqType, uri: SpringReqURI): SpringReqDSLBuilder = when (type) {
            SpringReqType.GET -> get(uri.name, uri.uriVariables)
            SpringReqType.PUT -> put(uri.name, uri.uriVariables)
            SpringReqType.POST -> post(uri.name, uri.uriVariables)
            SpringReqType.PATCH -> patch(uri.name, uri.uriVariables)
            SpringReqType.DELETE -> delete(uri.name, uri.uriVariables)
        }

        fun get(uri: String, uriVariables: List<Any>): SpringReqDSLBuilder {
            TODO()
        }

        fun put(uri: String, uriVariables: List<Any>): SpringReqDSLBuilder {
            TODO()
        }

        fun post(uri: String, uriVariables: List<Any>): SpringReqDSLBuilder {
            TODO()
        }

        fun patch(uri: String, uriVariables: List<Any>): SpringReqDSLBuilder {
            TODO()
        }

        fun delete(uri: String, uriVariables: List<Any>): SpringReqDSLBuilder {
            TODO()
        }
    }

    fun getDSL() = reqDSL
    fun addParam(attr: ParamAttr): SpringReqDSLBuilder {
        TODO("Return this")
    }

    fun addHeader(attr: HeaderAttr): SpringReqDSLBuilder {
        TODO("Return this")
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
    private val initStatements: List<UTestInst>,
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
    val cp: JcClasspath,
    private val initStatements: MutableList<UTestInst> = mutableListOf(),
    private val matchers: MutableList<UTestExpression> = mutableListOf(),
) {

    fun addStatusCheck(int: Int): SpringMatchersDSLBuilder {
        val statusMatcherDSL = UTestStaticMethodCall(
            method = cp.findJcMethod("org.springframework.test.web.servlet.result.MockMvcResultMatchers", "status").method,
            args = listOf()
        ).also { initStatements.add(it) }

        val intDSL = UTestIntExpression(
            value = int,
            //TODO: original method StatusResultMatchers.is() takes a primitive type int, so this may be incorrect
            type = cp.findType("java.lang.Integer")
        ).also { initStatements.add(it) }

        UTestMethodCall(
            instance = statusMatcherDSL,
            method = cp.findJcMethod("org.springframework.test.web.servlet.result.StatusResultMatchers", "is").method,
            args = listOf(intDSL)
        ).also { matchers.add(it) }

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getMatchersDSL(): List<UTestExpression> = matchers
}

class JcSpringTest private constructor(
    val generatedTestClass: JcClassType,
    /* Request information */
    val reqAttrs: List<SpringReqAttr>,
    val reqType: SpringReqType,
    val reqURI: SpringReqURI,
    /* Response information */
    val res: SpringResponse,
) {
    companion object {
        fun generateFromState(state: JcState): JcSpringTest {
            val generatedTestClass = getGeneratedClassName()
            val reqAttrs = getReqAttrs(/*TODO: should it be userDefinedValues?*/)
            val reqType = getReqType(/*TODO: (?)*/)
            val reqURI = getReqURI(/*TODO: (?)*/)
            val res = getSpringResponse(/*TODO: should it be state or something like UReadOnlyMemory<JcType>?*/)

            return JcSpringTest(
                generatedTestClass,
                reqAttrs,
                reqType,
                reqURI,
                res,
            )
        }

        private fun getGeneratedClassName(): JcClassType {
            TODO("from state")
        }

        private fun getReqAttrs(): MutableList<SpringReqAttr> {
            TODO("get concrete values and save them")
        }

        private fun getReqType(): SpringReqType {
            TODO("")
        }

        private fun getReqURI(): SpringReqURI {
            TODO("")
        }

        private fun getSpringResponse(): SpringResponse {
            TODO("Oh....")
        }
    }

    fun generateTestDSL(cp: JcClasspath): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecDSLBuilder.intiTestCtx(
            cp = cp,
            generatedTestClass = generatedTestClass,
            fromField = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field
        ).also { initStatements.addAll(it.getInitDSL()) }

        val reqDSL = generateReqDSL(reqType, reqURI, reqAttrs).also { initStatements.add(it) }

        testExecBuilder.addPerformCall(reqDSL)

        val (matchersDSL, matchersInitDSL) = generateMatchersDSL(cp)
        initStatements.addAll(matchersInitDSL)
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
        reqType: SpringReqType,
        reqPath: SpringReqURI,
        reqAttrs: List<SpringReqAttr>
    ): UTestExpression {
        val builder = SpringReqDSLBuilder.createReq(reqType, reqPath).addAttrs(reqAttrs)
        return builder.getDSL()
    }
}
