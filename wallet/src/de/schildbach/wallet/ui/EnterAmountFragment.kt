/*
 * Copyright 2019 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.schildbach.wallet.Constants
import de.schildbach.wallet.WalletApplication
import de.schildbach.wallet.ui.send.SharedViewModel
import de.schildbach.wallet.ui.widget.NumericKeyboardView
import de.schildbach.wallet_test.R
import kotlinx.android.synthetic.main.enter_amount_fragment.*
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Monetary
import org.bitcoinj.utils.ExchangeRate
import org.bitcoinj.utils.Fiat
import org.bitcoinj.utils.MonetaryFormat
import org.dash.wallet.common.Configuration
import org.dash.wallet.common.util.GenericUtils
import java.text.DecimalFormatSymbols

class EnterAmountFragment : Fragment() {

    companion object {
        private const val ARGUMENT_INITIAL_AMOUNT = "argument_initial_amount"

        @JvmStatic
        fun newInstance(initialAmount: Monetary = Coin.ZERO): EnterAmountFragment {
            val args = Bundle()
            args.putSerializable(ARGUMENT_INITIAL_AMOUNT, initialAmount)
            val enterAmountFragment = EnterAmountFragment()
            enterAmountFragment.arguments = args
            return enterAmountFragment
        }
    }

    private val friendlyFormat = MonetaryFormat.BTC.minDecimals(2).repeatOptionalDecimals(1, 6).noCode()

    private lateinit var viewModel: EnterAmountViewModel
    private lateinit var sharedViewModel: SharedViewModel

    private var config: Configuration? = null
    var exchangeRate: ExchangeRate? = null
    var displayEditedValue: Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.enter_amount_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        convert_direction.setOnClickListener {
            viewModel.dashToFiatDirectionData.value = !viewModel.dashToFiatDirectionValue
        }
        confirm_button.setOnClickListener {
            sharedViewModel.buttonClickEvent.call(sharedViewModel.dashAmount)
        }
        numeric_keyboard.enableDecSeparator(true);
        numeric_keyboard.onKeyboardActionListener = object : NumericKeyboardView.OnKeyboardActionListener {

            var value = StringBuilder()

            override fun onNumber(number: Int) {
                refreshValue()
                appendIfValidAfter(number)
                applyNewValue()
            }

            override fun onBack() {
                refreshValue()
                if (value.isNotEmpty()) {
                    value.deleteCharAt(value.length - 1)
                }
                applyNewValue()
            }

            override fun onFunction() {
                refreshValue()
                val decimalSeparator = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
                if (value.indexOf(decimalSeparator) == -1) {
                    value.append(decimalSeparator)
                }
                applyNewValue()
            }

            fun refreshValue() {
                value.clear()
                value.append(input_amount.text)
            }

            private fun appendIfValidAfter(number: Int) {
                try {
                    value.append(number)
                    Coin.parseCoin(value.toString())
                } catch (e: Exception) {
                    value.deleteCharAt(value.length - 1)
                }
            }

            fun applyNewValue() {
                displayEditedValue = false
                val strValue = value.toString()
                if (viewModel.dashToFiatDirectionValue) {
                    val dashValue = Coin.parseCoin(strValue)
                    viewModel.setDashAmount(dashValue)
                } else {
                    val fiatValue = Fiat.parseFiat(viewModel.fiatAmountData.value!!.currencyCode, strValue)
                    viewModel.setFiatAmount(fiatValue)
                }
                viewModel.calculateDependent(sharedViewModel.exchangeRate)
                input_amount.text = strValue
                displayEditedValue = true
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        initViewModels()

        calc_pane.visibility = View.GONE
        convert_direction.visibility = View.GONE

        if (arguments != null) {
            val initialAmount = arguments!!.getSerializable(ARGUMENT_INITIAL_AMOUNT) as Monetary
            viewModel.dashToFiatDirectionData.value = initialAmount is Coin
            if (viewModel.dashToFiatDirectionValue) {
                viewModel.setDashAmount(initialAmount as Coin)
            } else {
                viewModel.setFiatAmount(initialAmount as Fiat)
            }
        } else {
            viewModel.dashToFiatDirectionData.value = true
            viewModel.setDashAmount(Coin.ZERO)
        }
    }

    private fun initViewModels() {
        viewModel = ViewModelProviders.of(this)[EnterAmountViewModel::class.java]
        viewModel.dashToFiatDirectionData.observe(viewLifecycleOwner, Observer {
            input_symbol.visibility = if (it) View.GONE else View.VISIBLE
            input_symbol_dash.visibility = if (it) View.VISIBLE else View.GONE
            calc_amount_symbol.visibility = if (it) View.VISIBLE else View.GONE
            calc_amount_symbol_dash.visibility = if (it) View.GONE else View.VISIBLE
            viewModel.dashAmountData.value?.run {
                displayDashValue(this)
            }
            viewModel.fiatAmountData.value?.run {
                displayFiatValue(this)
            }
        })
        viewModel.dashAmountData.observe(viewLifecycleOwner, Observer {
            displayDashValue(it)
            sharedViewModel.dashAmountData.value = it
        })
        viewModel.fiatAmountData.observe(viewLifecycleOwner, Observer {
            displayFiatValue(it)
            val currencySymbol = GenericUtils.currencySymbol(it.currencyCode)
            input_symbol.text = currencySymbol
            calc_amount_symbol.text = currencySymbol
        })
        sharedViewModel = activity?.run {
            ViewModelProviders.of(this)[SharedViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        sharedViewModel.directionChangeEnabledData.observe(viewLifecycleOwner, Observer {
            convert_direction.isEnabled = it
        })
        sharedViewModel.buttonEnabledData.observe(viewLifecycleOwner, Observer {
            confirm_button.isEnabled = it
        })
        sharedViewModel.buttonTextData.observe(viewLifecycleOwner, Observer { it ->
            when {
                it > 0 -> confirm_button.setText(it)
                else -> confirm_button.text = null
            }
        })
        sharedViewModel.exchangeRateData.observe(viewLifecycleOwner, Observer {
            it?.also {
                calc_pane.visibility = View.VISIBLE
                convert_direction.visibility = View.VISIBLE
                viewModel.calculateDependent(sharedViewModel.exchangeRate)
            }
        })
        sharedViewModel.changeDashAmountEvent.observe(viewLifecycleOwner, Observer {
            viewModel.setDashAmount(it)
        })
    }

    private fun displayDashValue(value: Coin) {
        if (viewModel.dashToFiatDirectionValue) {
            if (displayEditedValue) {
                input_amount.text = friendlyFormat.format(value)
            }
        } else {
            calc_amount.text = friendlyFormat.format(value)
        }
    }

    private fun displayFiatValue(value: Fiat) {
        if (viewModel.dashToFiatDirectionValue) {
            calc_amount.text = Constants.LOCAL_FORMAT.format(value)
        } else {
            if (displayEditedValue) {
                input_amount.text = friendlyFormat.format(value)
            }
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        this.config = (context!!.applicationContext as WalletApplication).configuration
    }
}