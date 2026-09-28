package com.quistock.quistock.app.navigation

import com.quistock.quistock.R

object NavGraph {
    val GRAPH_RES_ID = R.navigation.nav_graph

    object Destinations {
        val LOGIN = R.id.loginFragment
        val HOME = R.id.homeFragment
        val REGISTER = R.id.cadastroPessoalFragment
        val INTERFERENCES = R.id.interferencesFragment
        val ORDER = R.id.orderFragment
        val PROMOTION = R.id.promotionFragment
    }

    object Actions {
        val LOGIN_TO_REGISTER = R.id.action_loginFragment_to_cadastroPessoalFragment
        val LOGIN_TO_HOME = R.id.action_loginFragment_to_homeFragment
        val REGISTER_TO_LOGIN = R.id.action_cadastroPessoalFragment_to_loginFragment
        val HOME_TO_INTERFERENCES = R.id.action_homeFragment_to_interferencesFragment
        val HOME_TO_ORDER = R.id.action_homeFragment_to_orderFragment
        val HOME_TO_PROMOTION = R.id.action_homeFragment_to_promotionFragment
    }
}
