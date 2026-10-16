package com.quistock.quistock.app.navigation.demo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentCadastroPessoalBinding
import com.quistock.quistock.databinding.FragmentChatbotBinding
import com.quistock.quistock.databinding.FragmentContactBinding
import com.quistock.quistock.databinding.FragmentEditEmployeeBinding
import com.quistock.quistock.databinding.FragmentEditProfileBinding
import com.quistock.quistock.databinding.FragmentEmployeeBinding
import com.quistock.quistock.databinding.FragmentEmployeeDetailBinding
import com.quistock.quistock.databinding.FragmentEmployeeProfileBinding
import com.quistock.quistock.databinding.FragmentFaqBinding
import com.quistock.quistock.databinding.FragmentHomeBinding
import com.quistock.quistock.databinding.FragmentInterferencesBinding
import com.quistock.quistock.databinding.FragmentLoadingBinding
import com.quistock.quistock.databinding.FragmentLoginBinding
import com.quistock.quistock.databinding.FragmentManagerInterferencesBinding
import com.quistock.quistock.databinding.FragmentManagerOrderApprovedBinding
import com.quistock.quistock.databinding.FragmentManagerOrderDetailBinding
import com.quistock.quistock.databinding.FragmentManagerPromoApprovedBinding
import com.quistock.quistock.databinding.FragmentManagerPromotionDetailBinding
import com.quistock.quistock.databinding.FragmentNotificationsBinding
import com.quistock.quistock.databinding.FragmentOnboardingBinding
import com.quistock.quistock.databinding.FragmentOrderBinding
import com.quistock.quistock.databinding.FragmentOrderSentBinding
import com.quistock.quistock.databinding.FragmentProductControlBinding
import com.quistock.quistock.databinding.FragmentProductDetailBinding
import com.quistock.quistock.databinding.FragmentProfileBinding
import com.quistock.quistock.databinding.FragmentPromoSentBinding
import com.quistock.quistock.databinding.FragmentPromotionBinding
import com.quistock.quistock.databinding.FragmentSplashBinding

private val pageBindings: Map<Int, (LayoutInflater, ViewGroup?, Boolean) -> ViewBinding> = mapOf(
    R.layout.fragment_splash to FragmentSplashBinding::inflate,
    R.layout.fragment_onboarding to FragmentOnboardingBinding::inflate,
    R.layout.fragment_login to FragmentLoginBinding::inflate,
    R.layout.fragment_home to FragmentHomeBinding::inflate,
    R.layout.fragment_profile to FragmentProfileBinding::inflate,
    R.layout.fragment_faq to FragmentFaqBinding::inflate,
    R.layout.fragment_contact to FragmentContactBinding::inflate,
    R.layout.fragment_edit_profile to FragmentEditProfileBinding::inflate,
    R.layout.fragment_notifications to FragmentNotificationsBinding::inflate,
    R.layout.fragment_chatbot to FragmentChatbotBinding::inflate,
    R.layout.fragment_order to FragmentOrderBinding::inflate,
    R.layout.fragment_promotion to FragmentPromotionBinding::inflate,
    R.layout.fragment_order_sent to FragmentOrderSentBinding::inflate,
    R.layout.fragment_promo_sent to FragmentPromoSentBinding::inflate,
    R.layout.fragment_interferences to FragmentInterferencesBinding::inflate,
    R.layout.fragment_manager_interferences to FragmentManagerInterferencesBinding::inflate,
    R.layout.fragment_manager_promotion_detail to FragmentManagerPromotionDetailBinding::inflate,
    R.layout.fragment_manager_order_detail to FragmentManagerOrderDetailBinding::inflate,
    R.layout.fragment_manager_promo_approved to FragmentManagerPromoApprovedBinding::inflate,
    R.layout.fragment_manager_order_approved to FragmentManagerOrderApprovedBinding::inflate,
    R.layout.fragment_product_control to FragmentProductControlBinding::inflate,
    R.layout.fragment_product_detail to FragmentProductDetailBinding::inflate,
    R.layout.fragment_employee to FragmentEmployeeBinding::inflate,
    R.layout.fragment_employee_detail to FragmentEmployeeDetailBinding::inflate,
    R.layout.fragment_employee_profile to FragmentEmployeeProfileBinding::inflate,
    R.layout.fragment_edit_employee to FragmentEditEmployeeBinding::inflate,
    R.layout.fragment_cadastro_pessoal to FragmentCadastroPessoalBinding::inflate,
    R.layout.fragment_loading to FragmentLoadingBinding::inflate,
)

internal fun inflateDemoPage(layout: Int, inflater: LayoutInflater, container: ViewGroup?): ViewBinding =
    requireNotNull(pageBindings[layout]) { "Unknown demo page" }(inflater, container, false)
