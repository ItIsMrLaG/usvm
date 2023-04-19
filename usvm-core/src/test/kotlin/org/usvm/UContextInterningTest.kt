package org.usvm

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UContextInterningTest {
    private lateinit var context: UContext

    @BeforeEach
    fun initializeContext() {
        context = UContext()
    }

    @Test
    fun testConcreteHeapRefInterning() = with(context) {
        val firstAddress = 1
        val secondAddress = 2

        val equal = List(10) { mkConcreteHeapRef(firstAddress) }

        val createdWithoutContext = UConcreteHeapRef(this, firstAddress)
        val distinct = listOf(
            mkConcreteHeapRef(firstAddress),
            mkConcreteHeapRef(secondAddress),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testRegisterReadingInterning() = with(context) {
        val fstSort = bv16Sort
        val sndSort = bv32Sort

        val fstIndex = 1
        val sndIndex = 2

        val equal = List(10) { mkRegisterReading(fstIndex, fstSort) }

        val createdWithoutContest = URegisterReading(context, fstIndex, fstSort)
        val distinct = listOf(
            mkRegisterReading(fstIndex, fstSort),
            mkRegisterReading(fstIndex, sndSort),
            mkRegisterReading(sndIndex, fstSort),
            mkRegisterReading(sndIndex, sndSort),
            createdWithoutContest
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testFieldReadingInterning() = with(context) {
        val fstRegion = mockk<UInputFieldRegion<Field, UBv32Sort>>()
        val sndRegion = mockk<UInputFieldRegion<Field, UBoolSort>>()

        every { fstRegion.sort } returns bv32Sort
        every { sndRegion.sort } returns boolSort

        val fstAddress = mkConcreteHeapRef(address = 1)
        val sndAddress = mkConcreteHeapRef(address = 2)

        val equal = List(10) { mkInputFieldReading(fstRegion, fstAddress) }

        val createdWithoutContext = UInputFieldReading(this, fstRegion, fstAddress)
        val distinct = listOf(
            mkInputFieldReading(fstRegion, fstAddress),
            mkInputFieldReading(fstRegion, sndAddress),
            mkInputFieldReading(sndRegion, fstAddress),
            mkInputFieldReading(sndRegion, sndAddress),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testAllocatedArrayReadingInterning() = with(context) {
        val fstRegion = mockk<UAllocatedArrayRegion<Type, UBv32Sort>>()
        val sndRegion = mockk<UAllocatedArrayRegion<Type, UBoolSort>>()

        every { fstRegion.sort } returns bv32Sort
        every { sndRegion.sort } returns boolSort

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val equal = List(10) { mkAllocatedArrayReading(fstRegion, fstIndex) }

        val createdWithoutContext = UAllocatedArrayReading(this, fstRegion, fstIndex)

        val distinct = listOf(
            mkAllocatedArrayReading(fstRegion, fstIndex),
            mkAllocatedArrayReading(fstRegion, sndIndex),
            mkAllocatedArrayReading(sndRegion, fstIndex),
            mkAllocatedArrayReading(sndRegion, sndIndex),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testInputArrayReadingInterning() = with(context) {
        val fstRegion = mockk<UInputArrayRegion<Type, UBv32Sort>>()
        val sndRegion = mockk<UInputArrayRegion<Type, UBoolSort>>()

        every { fstRegion.sort } returns bv32Sort
        every { sndRegion.sort } returns boolSort

        val fstAddress = mkConcreteHeapRef(address = 1)
        val sndAddress = mkConcreteHeapRef(address = 2)

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val equal = List(10) { mkInputArrayReading(fstRegion, fstAddress, fstIndex) }

        val createdWithoutContext = UInputArrayReading(this, fstRegion, fstAddress, fstIndex)

        val distinct = listOf(
            mkInputArrayReading(fstRegion, fstAddress, fstIndex),
            mkInputArrayReading(fstRegion, fstAddress, sndIndex),
            mkInputArrayReading(fstRegion, sndAddress, fstIndex),
            mkInputArrayReading(sndRegion, fstAddress, fstIndex),
            mkInputArrayReading(sndRegion, sndAddress, sndIndex),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }


    @Test
    fun testArrayLengthInterning() = with(context) {
        val fstRegion = mockk<UInputArrayLengthRegion<Type>>()
        val sndRegion = mockk<UInputArrayLengthRegion<Type>>()

        every { fstRegion.sort } returns sizeSort
        every { sndRegion.sort } returns sizeSort

        val fstAddress = mkConcreteHeapRef(address = 1)
        val sndAddress = mkConcreteHeapRef(address = 2)

        val equal = List(10) { mkInputArrayLengthReading(fstRegion, fstAddress) }

        val createdWithoutContext = UInputArrayLengthReading(this, fstRegion, fstAddress)

        val distinct = listOf(
            mkInputArrayLengthReading(fstRegion, fstAddress),
            mkInputArrayLengthReading(fstRegion, sndAddress),
            mkInputArrayLengthReading(sndRegion, sndAddress),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testIndexedMethodReturnValueInterning() = with(context) {
        val fstMethod = mockk<java.lang.reflect.Method>()
        val sndMethod = mockk<java.lang.reflect.Method>()

        val fstCallIndex = 1
        val sndCallIndex = 2

        val fstSort = bv16Sort
        val sndSort = bv32Sort

        val equal = List(10) { mkIndexedMethodReturnValue(fstMethod, fstCallIndex, fstSort) }

        val createdWithoutContext = UIndexedMethodReturnValue(this, fstMethod, fstCallIndex, fstSort)

        val distinct = listOf(
            mkIndexedMethodReturnValue(fstMethod, fstCallIndex, fstSort),
            mkIndexedMethodReturnValue(fstMethod, sndCallIndex, fstSort),
            mkIndexedMethodReturnValue(fstMethod, fstCallIndex, sndSort),
            mkIndexedMethodReturnValue(sndMethod, sndCallIndex, sndSort),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testIsExprInterning() = with(context) {
        val fstRef = mkConcreteHeapRef(address = 1)
        val sndRef = mkConcreteHeapRef(address = 2)

        val fstSort = bv16Sort // TODO replace with jacodb type
        val sndSort = bv32Sort

        val equal = List(10) { mkIsExpr(fstRef, fstSort) }

        val createdWithoutContext = UIsExpr(this, fstRef, fstSort)

        val distinct = listOf(
            mkIsExpr(fstRef, fstSort),
            mkIsExpr(fstRef, sndSort),
            mkIsExpr(sndRef, sndSort),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    private fun compare(
        equals: List<UExpr<out USort>>,
        distinct: List<UExpr<out USort>>
    ): Boolean {
        val internedCorrectly = equals.all { it === equals.first() }
        val distinctWorkCorrectly = distinct.distinct().size == distinct.size

        return internedCorrectly && distinctWorkCorrectly
    }
}