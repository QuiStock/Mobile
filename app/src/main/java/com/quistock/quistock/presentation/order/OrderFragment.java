package com.quistock.quistock.presentation.order;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.quistock.quistock.R;
import com.quistock.quistock.presentation.common.PopupFeedback;
import kotlin.Unit;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link OrderFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class OrderFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public OrderFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment OrderFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static OrderFragment newInstance(String param1, String param2) {
        OrderFragment fragment = new OrderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_order, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        EditText quantity = view.findViewById(R.id.etOrderQuantity);
        EditText date = view.findViewById(R.id.etOrderDate);
        Button submit = view.findViewById(R.id.btnSubmitOrder);
        submit.setOnClickListener(button -> {
            if (quantity.getText().toString().trim().isEmpty() || date.getText().toString().trim().isEmpty()) {
                PopupFeedback.showStatus(view, getString(R.string.popup_order_invalid), true);
                return;
            }
            PopupFeedback.showConfirmation(requireContext(), getString(R.string.popup_order_confirm), () -> {
                NavHostFragment.findNavController(this).navigate(R.id.action_orderFragment_to_orderSentFragment);
                return Unit.INSTANCE;
            });
        });
    }
}
