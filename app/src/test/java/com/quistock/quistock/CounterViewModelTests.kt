package com.quistock.quistock

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.presentation.viewmodel.CounterViewModel
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class CounterViewModelTests {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun incrementIncrementsCounter() {
        val vm = CounterViewModel()

        vm.increment()

        assertEquals(1, vm.counter.value)
    }

    @Test
    fun incrementIncrementsCounterTwice() {
        val vm = CounterViewModel()

        vm.increment()
        vm.increment()

        assertEquals(2, vm.counter.value)
    }
}
