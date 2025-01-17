/*
 * Copyright 2015-present the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dash.wallet.integration.uphold.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.dash.wallet.common.Configuration;
import org.dash.wallet.common.customtabs.CustomTabActivityHelper;
import org.dash.wallet.common.ui.CurrencyTextView;
import org.dash.wallet.common.ui.DialogBuilder;
import org.dash.wallet.integration.uphold.R;
import org.dash.wallet.integration.uphold.data.UpholdCard;
import org.dash.wallet.integration.uphold.data.UpholdClient;
import org.dash.wallet.integration.uphold.data.UpholdConstants;
import org.dash.wallet.integration.uphold.data.UpholdException;

import java.math.BigDecimal;

public class UpholdAccountActivity extends AppCompatActivity {

    public static final String WALLET_RECEIVING_ADDRESS_EXTRA = "uphold_receiving_address_extra";

    private CurrencyTextView balanceView;
    private BigDecimal balance;
    private String receivingAddress;
    private final MonetaryFormat monetaryFormat = new MonetaryFormat().noCode().minDecimals(8);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uphold_account_screen);

        receivingAddress = getIntent().getStringExtra(WALLET_RECEIVING_ADDRESS_EXTRA);
        if (receivingAddress == null) {
            finish();
        }

        balanceView = findViewById(R.id.uphold_account_balance);
        balanceView.setFormat(monetaryFormat);
        balanceView.setApplyMarkup(false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        if (actionBar != null) {
            actionBar.setTitle(R.string.uphold_account);
        }

        findViewById(R.id.uphold_transfer_to_this_wallet_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (balance != null && receivingAddress != null) {
                    showWithdrawalDialog();
                }
            }
        });
        findViewById(R.id.uphold_buy_dash_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBuyDashUrl();
            }
        });

        findViewById(R.id.uphold_logout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLogOutUrl();
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        if(item.getItemId() == R.id.uphold_logout) {
            openLogOutUrl();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserBalance();
    }

    private void loadUserBalance() {
        final ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(false);
        loadingDialog.setMessage(getString(R.string.loading));
        loadingDialog.show();

        UpholdClient.getInstance().getDashBalance(new UpholdClient.Callback<BigDecimal>() {
            @Override
            public void onSuccess(BigDecimal data) {
                balance = data;
                balanceView.setAmount(Coin.parseCoin(balance.toString()));
                loadingDialog.cancel();
            }

            @Override
            public void onError(Exception e, boolean otpRequired) {
                loadingDialog.cancel();

                if(e instanceof UpholdException) {
                    UpholdException ue = (UpholdException)e;
                    if(ue.getCode() == 401) {
                        //we don't have the correct access token
                        showAutoLogoutAlert();
                    } else
                        showErrorAlert(ue.getCode());
                } else showErrorAlert(-1);
            }
        });
    }

    private void openBuyDashUrl() {
        UpholdCard dashCard = UpholdClient.getInstance().getCurrentDashCard();
        if (dashCard != null) {
            final String url = String.format(UpholdConstants.CARD_URL_BASE, dashCard.getId());

            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            int toolbarColor = ContextCompat.getColor(this, R.color.colorPrimary);
            CustomTabsIntent customTabsIntent = builder.setShowTitle(true)
                    .setToolbarColor(toolbarColor).build();

            CustomTabActivityHelper.openCustomTab(this, customTabsIntent, Uri.parse(url),
                    new CustomTabActivityHelper.CustomTabFallback() {
                        @Override
                        public void openUri(Activity activity, Uri uri) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(url));
                            startActivity(intent);
                        }
                    });
        } else {
            showErrorAlert(-1);
        }
    }

    private void showErrorAlert(int code) {
        int messageId = R.string.loading_error;

        if(code == 400 || code == 408 || code >= 500)
            messageId = R.string.uphold_error_not_available;
        if(code == 400 || code == 403 || code >= 400)
                messageId = R.string.uphold_error_report_issue;

        new DialogBuilder(this)
                .setMessage(messageId)
                .setTitle(R.string.uphold_error)
                .show();
    }

    private void showWithdrawalDialog() {
        Configuration config = new Configuration(PreferenceManager.getDefaultSharedPreferences(this), getResources());
        String currencyCode = config.getFormat().code();
        MonetaryFormat inputFormat = config.getMaxPrecisionFormat();
        MonetaryFormat hintFormat = config.getFormat();

        UpholdWithdrawalDialog.show(getSupportFragmentManager(), balance,
                receivingAddress, currencyCode, inputFormat, hintFormat,
                new UpholdWithdrawalDialog.OnTransferListener() {
                    @Override
                    public void onTransfer() {
                        loadUserBalance();
                    }
                });
    }

    private void revokeAccessToken() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.uphold_logout_title);
        builder.setPositiveButton(R.string.uphold_logout_go_to_website, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                UpholdClient.getInstance().revokeAccessToken(new UpholdClient.Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        startUpholdSplashActivity();
                        openUpholdToLogout();
                    }

                    @Override
                    public void onError(Exception e, boolean otpRequired) {
                        if(e instanceof UpholdException) {
                            UpholdException ue = (UpholdException)e;
                            showErrorAlert(ue.getCode());
                        } else
                            showErrorAlert(-1);
                    }
                });
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.uphold_logout_confirm, null);
        builder.setView(dialogView);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void openUpholdToLogout() {
        final String url = UpholdConstants.LOGOUT_URL;

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        int toolbarColor = ContextCompat.getColor(UpholdAccountActivity.this, R.color.colorPrimary);
        CustomTabsIntent customTabsIntent = builder.setShowTitle(true)
                .setToolbarColor(toolbarColor).build();

        CustomTabActivityHelper.openCustomTab(UpholdAccountActivity.this, customTabsIntent, Uri.parse(url),
                new CustomTabActivityHelper.CustomTabFallback() {
                    @Override
                    public void openUri(Activity activity, Uri uri) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
                });

    }

    private void openLogOutUrl() {
        //revoke access to the token
        revokeAccessToken();
    }

    private void showAutoLogoutAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.My_Theme_Dialog);
        builder.setTitle(R.string.uphold_error);
        builder.setMessage(R.string.uphold_error_not_logged_in);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.uphold_link_account, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startUpholdSplashActivity();
            }
        });
        builder.show();
    }

    private void startUpholdSplashActivity() {
        Intent intent = new Intent(this, UpholdSplashActivity.class);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

}
