/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trivial.upv.android.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.databinding.DataBindingUtil;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.trivial.upv.android.R;
import com.trivial.upv.android.databinding.ActivityCategorySelectionBinding;
import com.trivial.upv.android.fragment.CategorySelectionFragment;
import com.trivial.upv.android.helper.ApiLevelHelper;
import com.trivial.upv.android.helper.PreferencesHelper;
import com.trivial.upv.android.model.Player;
import com.trivial.upv.android.persistence.TopekaJSonHelper;

import static com.trivial.upv.android.persistence.TopekaJSonHelper.ACTION_RESP;

public class CategorySelectionActivity extends AppCompatActivity {

    private static final String EXTRA_PLAYER = "player";

    public static void start(Activity activity, Player player, ActivityOptionsCompat options) {
        Intent starter = getStartIntent(activity, player);
        ActivityCompat.startActivity(activity, starter, options.toBundle());
    }

    public static void start(Context context, Player player) {
        Intent starter = getStartIntent(context, player);
        context.startActivity(starter);
    }

    @NonNull
    static Intent getStartIntent(Context context, Player player) {
        Intent starter = new Intent(context, CategorySelectionActivity.class);
        starter.putExtra(EXTRA_PLAYER, player);
        return starter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCategorySelectionBinding binding = DataBindingUtil
                .setContentView(this, R.layout.activity_category_selection);
        Player player = getIntent().getParcelableExtra(EXTRA_PLAYER);
        if (!PreferencesHelper.isSignedIn(this)) {
            if (player == null) {
                player = PreferencesHelper.getPlayer(this);
            } else {
                PreferencesHelper.writeToPreferences(this, player);
            }
        }
        binding.setPlayer(player);
        setUpToolbar();

        initActivity(savedInstanceState);
    }

    // JVG.S
    private void loadCategories() {
        // RECEIVER PARA ACTUALIZAR PROGRESO Y CARGA DE LAS CATEGORIAS
        filtro = new IntentFilter(ACTION_RESP);
        filtro.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new ReceptorOperacion();

        // Carga categorias
        int numCategorias = 0;
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.category_container);
        if (fragment instanceof CategorySelectionFragment) {
            numCategorias = ((CategorySelectionFragment) fragment).getAdapter().getItemCount();

        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        if (!TopekaJSonHelper.getInstance(CategorySelectionActivity.this, false).isLoaded() || numCategorias == 0) {
            if (checkInternetAccess()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

                pDialog = new ProgressDialog(this);
                pDialog.setMessage("Cargando...");
                pDialog.setIndeterminate(false);
                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setProgress(0);
                pDialog.setMax(100);
                pDialog.setCancelable(false);
                pDialog.show();

                new Thread() {
                    public void run() {

                        TopekaJSonHelper.getInstance(CategorySelectionActivity.this, true);

                    }
                }.start();

            } else {
                Snackbar snackbar = Snackbar
                        .make(findViewById(R.id.category_container), "No hay conexión de Internet.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("CERRAR", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                finish();
                            }
                        });

                snackbar.show();
            }
        } else {
//            showToolbarSubcategories();
        }
    }

    private boolean checkInternetAccess() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    IntentFilter filtro;

    ReceptorOperacion receiver = null;

    private Snackbar snackbar = null;
    public void showDeleteProgressConfirmation(final int position) {
        snackbar = Snackbar.make(findViewById(R.id.root_view), "¿Quieres eliminar los resultados obtenidos?", Snackbar.LENGTH_INDEFINITE).setAction("Eliminar Avance", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                (TopekaJSonHelper.getInstance(getBaseContext(), false)).deleteProgressCategory(position);

                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.category_container);
                if (fragment instanceof CategorySelectionFragment) {
                    ((CategorySelectionFragment) fragment).getAdapter().updateCategories();
                    ((CategorySelectionFragment) fragment).getAdapter().notifyItemChanged(position);

                }
                snackbar.dismiss();
                snackbar = null;
            }
        });

        snackbar.show();
    }

    public class ReceptorOperacion extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            synchronized (this) {
                String result = intent.getExtras().getString("RESULT");


                if ("OK".equals(result)) {
                    if (pDialog != null) {
                        pDialog.dismiss();
                        pDialog = null;
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }

                    // Carga categorias
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.category_container);
                    if (fragment instanceof CategorySelectionFragment) {
                        ((CategorySelectionFragment) fragment).animateTransitionSubcategories(null);
                    }
                    Log.d("ONRECEIVE", intent.getExtras().getString("RESULT"));

                } else if ("REFRESH".equals(result)) {
                    if (pDialog != null) {
                        pDialog.setProgress(intent.getExtras().getInt("REFRESH", 0));
                    }
                } else if ("ERROR".equals(result)) {
                    Snackbar snackbar = Snackbar
                            .make(findViewById(R.id.root_view), "Ha ocurrido un error cargando las categorías", Snackbar.LENGTH_INDEFINITE)
                            .setAction("CERRAR", new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    finish();
                                }
                            });

                    if (pDialog != null) {
                        pDialog.dismiss();
                        pDialog = null;
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

                    }

                    Log.d("ONRECEIVE", intent.getExtras().getString("RESULT"));

                    TopekaJSonHelper.getInstance(CategorySelectionActivity.this, false).resetData();

                    snackbar.show();
                }
            }
        }

    }
    // JVG.E

    private void initActivity(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            attachCategoryGridFragment();
        } else {
            setProgressBarVisibility(View.GONE);
        }
        supportPostponeEnterTransition();
    }

    //JVG.S
    @Override
    public void onBackPressed() {
        if (snackbar!=null )
        {
            snackbar.dismiss();
            snackbar = null;
        }
        else if (!TopekaJSonHelper.getInstance(getBaseContext(), false).thereAreMorePreviusCategories()) {
            super.onBackPressed();
        } else {
            goToPreviusCategory();
        }
    }

    private void goToPreviusCategory() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.category_container);
        if (fragment instanceof CategorySelectionFragment) {
            TopekaJSonHelper.getInstance(getBaseContext(), false).navigatePreviusCategory();
            ((CategorySelectionFragment) fragment).animateTransitionSubcategories(null);
            showToolbarSubcategories();
        }
    }

    private void showToolbarSubcategories() {
        if (TopekaJSonHelper.getInstance(getBaseContext(), false).isInitCategory()) {
            animateToolbarNavigateCategories();
        } else {
            TextView viewSubcategoryText = (TextView) findViewById(R.id.sub_category_title);
            viewSubcategoryText.setText(TopekaJSonHelper.getInstance(getBaseContext(), false).getPreviousTitleCategory());
            Log.d("previus", "categoria_previa" + TopekaJSonHelper.getInstance(getBaseContext(), false).getPreviousTitleCategory());
            animateToolbarNavigateToSubcategories();
        }
    }


    public ProgressDialog pDialog;
    //JVG.E

    @Override
    protected void onResume() {
        super.onResume();
        TextView scoreView = (TextView) findViewById(R.id.score);
        TextView scoreViewMain = (TextView) findViewById(R.id.score_main);

//      JVG.S
//        final int score = TopekaDatabaseHelper.getScore(this);
        final int score = TopekaJSonHelper.getInstance(getBaseContext(), false).getScore();

//      JVG.E
        scoreView.setText(getString(R.string.x_points, score));
        scoreViewMain.setText(getString(R.string.x_points, score));
    }

    /*JVG.S*/
    @Override
    protected void onStart() {
        super.onStart();

        // JVG.S
        loadCategories();
        // JVG.E
        registerReceiver(receiver, filtro);
    } //JVG.E

    private void setUpToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_player);
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        ImageButton back = (ImageButton) findViewById(R.id.back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToPreviusCategory();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_category, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.category_container);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out: {
                signOut();
                return true;
            }
//            case android.R.id.home:
//                goToPreviusCategory();
//                Log.d("BACK", "BACK PULSED");
//                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("NewApi")
    private void signOut() {
        PreferencesHelper.signOut(this);
//        JVG.S
//        TopekaDatabaseHelper.reset(this);
        TopekaJSonHelper.getInstance(this, false).signOut(getBaseContext());

//        JVG.E
        if (ApiLevelHelper.isAtLeast(Build.VERSION_CODES.LOLLIPOP)) {
            getWindow().setExitTransition(TransitionInflater.from(this)
                    .inflateTransition(R.transition.category_enter));
        }
        SignInActivity.start(this, false);
        finish();
    }

    private void attachCategoryGridFragment() {
        FragmentManager supportFragmentManager = getSupportFragmentManager();
        Fragment fragment = supportFragmentManager.findFragmentById(R.id.category_container);
        if (!(fragment instanceof CategorySelectionFragment)) {
            fragment = CategorySelectionFragment.newInstance();
        }
        supportFragmentManager.beginTransaction()
                .replace(R.id.category_container, fragment)
                .commit();
        setProgressBarVisibility(View.GONE);
    }

    private void setProgressBarVisibility(int visibility) {
        /// JVG.S
        /// findViewById(R.id.progress).setVisibility(visibility);
        /// JVG.E
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }

        if (pDialog != null) {
            pDialog.dismiss();
            pDialog = null;
        }

        if (snackbar!=null) {
            snackbar.dismiss();
            snackbar = null;
        }


        super.onStop();
    }

    // Scale in X and Y a view, with a duration and a start delay
    private void animateViewFullScaleXY(View view, int startDelay, int duration) {
        view.setScaleX(0f);
        view.setScaleY(0f);

        ViewCompat.animate(view)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f);
    }

    public void animateToolbarNavigateCategories() {
        View viewSubcategoryData = findViewById(R.id.subcategory_data);
        viewSubcategoryData.setVisibility(View.GONE);

        View viewLoginData = findViewById(R.id.login_data);
        viewLoginData.setVisibility(View.VISIBLE);

        View avatar = findViewById(R.id.avatar);
        animateViewFullScaleXY(avatar, 200, 300);

        View textViewCategory = findViewById(R.id.title);
        animateViewFullScaleXY(textViewCategory, 300, 300);

        View score = findViewById(R.id.score_main);
        animateViewFullScaleXY(score, 400, 300);
    }
    //JVG.E

    public void animateToolbarNavigateToSubcategories() {
        View viewSubcategoryData = findViewById(R.id.subcategory_data);
        viewSubcategoryData.setVisibility(View.VISIBLE);

        View viewLoginData = findViewById(R.id.login_data);
        viewLoginData.setVisibility(View.GONE);

        View back = findViewById(R.id.back);
        animateViewFullScaleXY(back, 200, 300);

        View textViewSubcategory = findViewById(R.id.sub_category_title);
        animateViewFullScaleXY(textViewSubcategory, 300, 300);

        View score = findViewById(R.id.score);
        animateViewFullScaleXY(score, 400, 300);

    }
}

