package org.usvm.api

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.MethodNotFoundException
import org.usvm.machine.state.JcState
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.toType
import org.usvm.*
import org.usvm.api.util.JcTestStateResolver.ResolveMode
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestCall
import org.usvm.instrumentation.testcase.api.UTestClassExpression
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestGetFieldExpression
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestIntExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.instrumentation.testcase.api.UTestStringExpression
import org.usvm.instrumentation.util.stringType
import org.usvm.machine.JcContext
import org.usvm.machine.state.concreteMemory.JcConcreteMemory
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
    val valueType: JcType,
) : SpringReqAttr

data class HeaderAttr(
    val name: String,
    val values: List<Any>,
    val valueType: JcType,
) : SpringReqAttr

data class SpringReqPath(
    val path: String,
    val pathVariables: List<Any>
)

enum class SpringReqKind {
    GET,
    PUT,
    POST,
    PATCH,
    DELETE;

    override fun toString(): String {
        return when (this) {
            GET -> "get"
            PUT -> "put"
            POST -> "post"
            PATCH -> "patch"
            DELETE -> "delete"
        }
    }

    companion object {
        fun fromString(str: String): SpringReqKind =
            when (str) {
                GET.toString() -> GET
                PUT.toString() -> PUT
                POST.toString() -> POST
                PATCH.toString() -> PATCH
                DELETE.toString() -> DELETE
                else -> throw IllegalArgumentException("Unsupported kind: $str")
            }
    }

}

enum class SpringReqSettings {
    PATH,
    KIND,
}

data class SpringResponse(
    val statusCode: Int,
    // TODO (add needed)
)

class SpringExn

class SpringReqDSLBuilder private constructor(
    private val initStatements: MutableList<UTestInst>,
    private var reqDSL: UTestExpression,
    private val ctx: JcContext
) {
    companion object {

        fun createReq(ctx: JcContext, kind: SpringReqKind, path: SpringReqPath): SpringReqDSLBuilder =
            commonReqDSLBuilder(kind.toString(), ctx, path.path, path.pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CP =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private fun commonReqDSLBuilder(
            type: String,
            ctx: JcContext,
            path: String,
            pathVariables: List<Any>
        ): SpringReqDSLBuilder {
            val staticMethod = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, type).method
            val initDSL = mutableListOf<UTestInst>()
            val argsDSL = mutableListOf<UTestExpression>()
            argsDSL.add(UTestStringExpression(path, ctx.cp.stringType()))
            argsDSL.addAll(pathVariables.map { UTestStringExpression(it.toString(), ctx.cp.stringType()) })

            return SpringReqDSLBuilder(
                initStatements = initDSL,
                reqDSL = UTestStaticMethodCall(staticMethod, argsDSL),
                ctx = ctx
            )
        }
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getDSL() = reqDSL

    fun addParam(attr: ParamAttr): SpringReqDSLBuilder {
        val method = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, "param").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    fun addHeader(attr: HeaderAttr): SpringReqDSLBuilder {
        val method = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, "header").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    private fun addStrArrOfStrCallDSL(mName: JcMethod, str: String, arrOfStr: List<Any>) {
        val argsDSL = mutableListOf<UTestExpression>()
        argsDSL.add(UTestStringExpression(str, ctx.cp.stringType()))
        argsDSL.addAll(arrOfStr.map { UTestStringExpression(it.toString(), ctx.cp.stringType()) })
        UTestMethodCall(
            instance = reqDSL,
            method = mName,
            args = argsDSL,
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
    private val ctx: JcContext,
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
            ctx: JcContext,
            generatedTestClass: JcClassType,
            fromField: JcField
        ): SpringTestExecDSLBuilder {
            val initStatements = mutableListOf<UTestInst>()

            val testCtxManagerName = "org.springframework.test.context.TestContextManager"
            val testCtxManagerDSL = UTestConstructorCall(
                method = ctx.cp.findJcMethod(testCtxManagerName, "<init>").method,
                args = listOf(UTestClassExpression(generatedTestClass))
            ).also { initStatements.add(it) }

            val generatedClassInstDSL = UTestConstructorCall(
                method = ctx.cp.findJcMethod(generatedTestClass.name, "<init>").method,
                args = listOf()
            ).also { initStatements.add(it) }

            UTestMethodCall(
                instance = testCtxManagerDSL,
                method = ctx.cp.findJcMethod(testCtxManagerName, "prepareTestInstance").method,
                args = listOf(generatedClassInstDSL)
            ).also { initStatements.add(it) }

            val mockMvcDSL = UTestGetFieldExpression(
                instance = generatedClassInstDSL,
                field = fromField,
            ).also { initStatements.add(it) }

            return SpringTestExecDSLBuilder(
                ctx = ctx,
                initStatements = initStatements,
                mockMvcDSL = mockMvcDSL,
            )
        }
    }

    fun addPerformCall(reqDSL: UTestExpression): SpringTestExecDSLBuilder {
        UTestMethodCall(
            instance = mockMvcDSL,
            method = ctx.cp.findJcMethod("org.springframework.test.web.servlet.MockMvc", "perform").method,
            args = listOf(reqDSL)
        ).also {
            mockMvcDSL = it
            isPerformed = true
        }
        return this
    }

    fun addAndExpectCall(args: List<UTestExpression>): SpringTestExecDSLBuilder {
        check(isPerformed)

        UTestMethodCall(
            instance = mockMvcDSL,
            method = ctx.cp.findJcMethod("org.springframework.test.web.servlet.ResultActions", "andExpect").method,
            args = args
        ).also {
            mockMvcDSL = it
        }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getExecDSL(): UTestCall {
        check(isPerformed)
        return mockMvcDSL as UTestCall
    }
}

class SpringMatchersDSLBuilder(
    val ctx: JcContext
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"

    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    fun addStatusCheck(int: Int): SpringMatchersDSLBuilder {
        val statusMatcherDSL = UTestStaticMethodCall(
            method = ctx.cp.findJcMethod(
                "$SPRING_RESULT_PACK.MockMvcResultMatchers",
                "status"
            ).method,
            args = listOf()
        ).also { initStatements.add(it) }

        val intDSL = UTestIntExpression(
            value = int,
            type = ctx.cp.int
        ).also { initStatements.add(it) }

        UTestMethodCall(
            instance = statusMatcherDSL,
            method = ctx.cp.findJcMethod("$SPRING_RESULT_PACK.StatusResultMatchers", "is").method,
            args = listOf(intDSL)
        ).also { matchers.add(it) }

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getMatchersDSL(): List<UTestExpression> = matchers
}


class JcSpringTest private constructor(
    val ctx: JcContext,
    val generatedTestClass: JcClassType,
    /* Request information */
    val reqAttrs: List<SpringReqAttr>,
    val reqKind: SpringReqKind,
    val reqPath: SpringReqPath,
    /* Response information */
    private val _res: SpringResponse?,
    private val _exn: SpringExn?,
//    todo: exn
) {
    companion object {
        val REQUEST_MOD = ResolveMode.MODEL
        val RESPONSE_MOD = ResolveMode.CURRENT

        fun generateFromState(state: JcState): JcSpringTest =
            if (state.res != null)
                generateResponseTest(state)
            else
                generateExnTest(state)

        private fun generateResponseTest(state: JcState): JcSpringTest = JcSpringTest(
            state.ctx,
            getGeneratedClassName(state.ctx.cp),
            getReqAttrs(state),
            getReqKind(state),
            getReqPath(state),
            _res = getSpringResponse(state.ctx.cp, state),
            _exn = null
        )

        private fun generateExnTest(state: JcState): JcSpringTest = JcSpringTest(
            state.ctx,
            getGeneratedClassName(state.ctx.cp),
            getReqAttrs(state),
            getReqKind(state),
            getReqPath(state),
            _res = null,
            _exn = getSpringExn(),
        )

        private fun getSpringExn(): SpringExn {
            TODO()
        }

        private fun getGeneratedClassName(cp: JcClasspath): JcClassType {
            val cl = cp.findClassOrNull("StartSpringTestClass") //TODO: get from "WebBench.kt"
            check(cl != null)
            return cl.toType()
        }

        private fun getReqKind(state: JcState): SpringReqKind {
            val expr = state.reqSetup[SpringReqSettings.KIND] ?: throw IllegalArgumentException("No path found")
            val type = state.ctx.stringType as JcClassType
            val kind = concretizeSimple(REQUEST_MOD, state, expr, type)
            check(kind != null)

            return SpringReqKind.fromString(kind as String)
        }

        private fun getReqPath(state: JcState): SpringReqPath {
            fun sortReqParam(path: String, params: Map<String, Any>): List<Any> {
//                TODO: check it
                val paramNames = Regex("\\{([^}]*)}").findAll(path)
                    .map { it.groupValues[1] }
                    .toList()

                return paramNames.map {
                    params.getValue(it)
                }.also { check(it.size == params.size) }
            }

            val expr = state.reqSetup[SpringReqSettings.PATH] ?: throw IllegalArgumentException("No path found")
            val type = state.ctx.stringType as JcClassType
            val path = concretizeSimple(REQUEST_MOD, state, expr, type)
            check(path != null)

            val concreteReqParams = mutableMapOf<String, Any>().also { map ->
                state.userDefinedValues.forEach { (key, value) ->
                    if (key.contains("PATH_*".toRegex())) {
                        val name = key.split("_").also { it.subList(1, it.size) }.joinToString("_")
                        concretizeSimple(REQUEST_MOD, state, value.first, value.second).also {
                            check(it != null) //TODO: is it correct? (param have name? but == null)
                            map[name] = it
                        }
                    }
                }
            }

            return SpringReqPath(
                path = path as String,
                pathVariables = sortReqParam(path, concreteReqParams)
            )
        }

        private fun getReqAttrs(state: JcState): MutableList<SpringReqAttr> {
            fun concretize(expr: UExpr<out USort>, type: JcType) =
                concretizeAsList(REQUEST_MOD, state, expr, type)

            fun getHeaderAttr(name: String, expr: UExpr<out USort>, type: JcType) = concretize(expr, type)?.let {
                HeaderAttr(
                    name = name,
                    values = it,
                    valueType = type
                )
            }

            fun getParamAttr(name: String, expr: UExpr<out USort>, type: JcType) = concretize(expr, type)?.let {
                ParamAttr(
                    name = name,
                    values = it,
                    valueType = type
                )
            }

            return mutableListOf<SpringReqAttr>().also { list ->
                state.userDefinedValues.forEach { (key, value) ->
                    //TODO: (MCHK): null -> no attr
                    when {
                        key.contains("PARAM_*".toRegex()) -> {
                            val name = key.split("_").let { it.subList(1, it.size) }.joinToString("_")
                            getHeaderAttr(name, value.first, value.second)
                        }

                        key.contains("HEADER_*".toRegex()) -> {
                            val name = key.split("_").let { it.subList(1, it.size) }.joinToString("_")
                            getParamAttr(name, value.first, value.second)
                        }

                        else -> null
                    }?.also { list.add(it) }
                }
            }
        }

        private fun getSpringResponse(cp: JcClasspath, state: JcState): SpringResponse {
            check(state.res != null)
            val expr = state.res ?: throw IllegalArgumentException("No Response")
            val valueExpr = state.models[0].eval(expr)
            val type = cp.int
            val statusCode = concretizeSimple(
                RESPONSE_MOD,
                state,
                valueExpr,
                type
            ) as Int
            return SpringResponse(statusCode)
        }

        private fun concretizeSimple(mode: ResolveMode, state: JcState, expr: UExpr<out USort>, type: JcType) =
            (state.memory as JcConcreteMemory).concretize(
                state, state.models[0].eval(expr), type, mode
            )

        private fun concretizeAsList(mode: ResolveMode, state: JcState, expr: UExpr<out USort>, type: JcType) =
            concretizeSimple(mode, state, expr, type)?.let { value ->
                if (value is Iterable<*>) value.map { it!! }.toList()
                else listOf(value)
            }
    }

    val isSuccess get() = _res != null
    val isFail get() = _exn != null
    val res get() = check(isSuccess).let { _res!! }
    val exn get() = check(isFail).let { _exn!! }

    fun generateTestDSL(): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecDSLBuilder.intiTestCtx(
            ctx = ctx,
            generatedTestClass = generatedTestClass,
            fromField = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field
        ).also { initStatements.addAll(it.getInitDSL()) }

        val reqDSL = generateReqDSL(reqKind, reqPath, reqAttrs).let { (reqDSL, reqInitDSL) ->
            initStatements.addAll(reqInitDSL)
            reqDSL
        }
        testExecBuilder.addPerformCall(reqDSL)

        val matchersDSL = generateMatchersDSL().let { (matchersDSL, matchersInitDSL) ->
            initStatements.addAll(matchersInitDSL)
            matchersDSL
        }
        matchersDSL.forEach { testExecBuilder.addAndExpectCall(listOf(it)) }

        return UTest(
            initStatements = initStatements,
            callMethodExpression = testExecBuilder.getExecDSL()
        )
    }

    private fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> {
        val matchersBuilder = SpringMatchersDSLBuilder(ctx)

        matchersBuilder.addStatusCheck(res.statusCode)
//      TODO("add more matchers")

        return Pair(matchersBuilder.getMatchersDSL(), matchersBuilder.getInitDSL())
    }

    private fun generateReqDSL(
        reqKind: SpringReqKind,
        reqPath: SpringReqPath,
        reqAttrs: List<SpringReqAttr>
    ): Pair<UTestExpression, List<UTestInst>> {
        val builder = SpringReqDSLBuilder.createReq(ctx, reqKind, reqPath).addAttrs(reqAttrs)
        return Pair(builder.getDSL(), builder.getInitDSL())
    }
}
