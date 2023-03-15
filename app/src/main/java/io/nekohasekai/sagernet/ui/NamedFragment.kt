package io.nekohasekai.sagernet.ui

import androidx.fragment.app.Fragment

abstract class NamedFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    private val name by lazy { name0() }
    fun name() = name
    protected abstract fun name0(): String

}