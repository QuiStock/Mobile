package com.quistock.quistock.app.navigation

import com.quistock.quistock.R

object NavGraph {
    val GRAPH_RES_ID = R.navigation.nav_graph

    object Destinations {
        val SPLASH = R.id.splashFragment
        val ONBOARDING = R.id.onboardingFragment
        val LOGIN = R.id.loginFragment
        val HOME = R.id.homeFragment
        val REGISTER = R.id.cadastroPessoalFragment
        val INTERFERENCES = R.id.interferencesFragment
        val ORDER = R.id.orderFragment
        val PROMOTION = R.id.promotionFragment
        val ORDER_SENT = R.id.orderSentFragment
        val PROMO_SENT = R.id.promoSentFragment
        val PRODUCT_CONTROL = R.id.productControlFragment
        val PRODUCT_DETAIL = R.id.productDetailFragment
        val CHATBOT = R.id.chatbotFragment
    }

    object Actions {
        val SPLASH_TO_ONBOARDING = R.id.action_splashFragment_to_onboardingFragment
        val ONBOARDING_TO_LOGIN = R.id.action_onboardingFragment_to_loginFragment
        val LOGIN_TO_REGISTER = R.id.action_loginFragment_to_cadastroPessoalFragment
        val LOGIN_TO_HOME = R.id.action_loginFragment_to_homeFragment
        val REGISTER_TO_LOGIN = R.id.action_cadastroPessoalFragment_to_loginFragment
        val HOME_TO_INTERFERENCES = R.id.action_homeFragment_to_interferencesFragment
        val HOME_TO_ORDER = R.id.action_homeFragment_to_orderFragment
        val HOME_TO_PROMOTION = R.id.action_homeFragment_to_promotionFragment
        val HOME_TO_CHATBOT = R.id.action_homeFragment_to_chatbotFragment
        val ORDER_TO_ORDER_SENT = R.id.action_orderFragment_to_orderSentFragment
        val PROMOTION_TO_PROMO_SENT = R.id.action_promotionFragment_to_promoSentFragment
        val ORDER_SENT_TO_INTERFERENCES = R.id.action_orderSentFragment_to_interferencesFragment
        val PROMO_SENT_TO_INTERFERENCES = R.id.action_promoSentFragment_to_interferencesFragment
        val HOME_TO_PRODUCT_CONTROL = R.id.action_homeFragment_to_productControlFragment
        val PRODUCT_CONTROL_TO_PRODUCT_DETAIL = R.id.action_productControlFragment_to_productDetailFragment
    }
}
