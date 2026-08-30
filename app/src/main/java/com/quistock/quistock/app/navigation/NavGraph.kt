package com.quistock.quistock.app.navigation

import com.quistock.quistock.R

object NavGraph {
    const val GRAPH_RES_ID = R.navigation.nav_graph

    object Destinations {
        const val LOGIN = R.id.loginFragment
        const val REGISTER = R.id.cadastroPessoalFragment
    }

    object Actions {
        const val LOGIN_TO_REGISTER = R.id.action_loginFragment_to_cadastroPessoalFragment
        const val REGISTER_TO_LOGIN = R.id.action_cadastroPessoalFragment_to_loginFragment
    }
}
