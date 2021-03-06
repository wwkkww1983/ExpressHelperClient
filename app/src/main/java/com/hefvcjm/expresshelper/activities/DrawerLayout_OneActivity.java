package com.hefvcjm.expresshelper.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.hefvcjm.expresshelper.R;
import com.hefvcjm.expresshelper.activities.nav_activities.AboutActivity;
import com.hefvcjm.expresshelper.activities.nav_activities.MeActivity;
import com.hefvcjm.expresshelper.activities.nav_activities.SecurityActivity;
import com.hefvcjm.expresshelper.activities.nav_activities.SettingActivity;
import com.hefvcjm.expresshelper.adapter.ExpressListAdapter;
import com.hefvcjm.expresshelper.express.ExpressInfos;
import com.hefvcjm.expresshelper.net.MyHttpClient;
import com.hefvcjm.expresshelper.staticinfos.StaticInfos;
import com.hefvcjm.expresshelper.storage.Storage;
import com.hefvcjm.expresshelper.user.UserInfos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import cn.jpush.android.api.JPushInterface;


/**
 * 通过模板生成的DrawerLayout+NavigationView布局,也是最常见的APP展示方式
 */
public class DrawerLayout_OneActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public static final int REQUESTCODE_STATE_CHAGED = 1;

    private List<ExpressInfos> expressList = new ArrayList<ExpressInfos>();
    private String url;
    private String phone;
    private ExpressListAdapter adapter;

    private ListView lv_express_list;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_express_list);

//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//
//        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
//        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
//                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
//        drawer.setDrawerListener(toggle);
//        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        url = getResources().getString(R.string.str_server_url) + "/query/expresses";
        Intent intent = getIntent();
        phone = intent.getStringExtra("phone");
        StaticInfos.setPhone(phone);
        UserInfos.getInstance().setPhone(phone);
        JPushInterface.setAlias(DrawerLayout_OneActivity.this, 0, phone);
        registerMessageReceiver();
//        Collections.sort(expressList);
        adapter = new ExpressListAdapter(DrawerLayout_OneActivity.this, R.layout.item_list_express, expressList);
        lv_express_list = (ListView) findViewById(R.id.lv_express_list);
        lv_express_list.setAdapter(adapter);
        synchronize_express(url, phone);


        lv_express_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ExpressInfos expressInfos = expressList.get(position);
                Intent intent = new Intent(DrawerLayout_OneActivity.this, ExpressDetailActivity.class);
                intent.putExtra("express_detail", expressInfos.toString());
                intent.putExtra("position", position);
                Log.d("listview", expressInfos.toString());
                startActivityForResult(intent, REQUESTCODE_STATE_CHAGED);
            }
        });
    }


    public void doClick(View view) {
        switch (view.getId()) {
            case R.id.iv_title_logo:
                drawerLayout.openDrawer(Gravity.LEFT);
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED,
                        Gravity.RIGHT);
                break;
            default:
                break;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUESTCODE_STATE_CHAGED:
                try {
                    JSONObject json = new JSONObject(data.getStringExtra("result"));
                    if ((boolean) json.get("ischanged")) {
                        int position = (int) json.get("position");
                        if (position != -1) {
                            String state = json.getString("state");
                            int visibleFirstPosition = lv_express_list.getFirstVisiblePosition();
                            int visibleLastPosition = lv_express_list.getLastVisiblePosition();
                            if (position >= visibleFirstPosition && position <= visibleLastPosition) {
                                View view = lv_express_list.getChildAt(position - visibleFirstPosition);
                                ExpressListAdapter.ViewHolder holder = (ExpressListAdapter.ViewHolder) view.getTag();
                                holder.tv_express_state.setText(state);
                                if (state.equals(getResources().getString(R.string.str_state_receiving))) {
                                    holder.tv_express_state.setTextColor(getResources().getColor(R.color.state_receiving));
                                    holder.iv_state.setImageDrawable(getResources().getDrawable(R.drawable.receiving));
                                } else if (state.equals(getResources().getString(R.string.str_state_received))) {
                                    holder.tv_express_state.setTextColor(getResources().getColor(R.color.state_received));
                                    holder.iv_state.setImageDrawable(getResources().getDrawable(R.drawable.received));
                                } else if (state.equals(getResources().getString(R.string.str_state_refused))) {
                                    holder.tv_express_state.setTextColor(getResources().getColor(R.color.state_refused));
                                    holder.iv_state.setImageDrawable(getResources().getDrawable(R.drawable.refused));
                                }
                                ExpressInfos expressInfos = expressList.get(position);
                                expressInfos.setState(state);
                                expressList.set(position, expressInfos);
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }
    }

    private void synchronize_express(final String url, String phone) {
        try {
            new MyHttpClient(url
                    , new JSONObject().put("Content-Type", "application/json;charset=utf-8")
                    , new JSONObject().put("phone", phone)
                    , new MyHttpClient.ResponseListener() {
                @Override
                public void onResponse(String body, JSONObject headers) {
                    if (body == null) {
                        return;
                    }
                    new AsyncTask<String, Integer, List<ExpressInfos>>() {

                        @Override
                        protected List<ExpressInfos> doInBackground(String... strings) {
                            String result = strings[0];
                            List<ExpressInfos> temp = new ArrayList<ExpressInfos>();
                            try {
                                JSONObject js = new JSONObject(result);
                                int n = new Integer(js.getString("total"));
                                Log.d("test", n + "");
                                JSONArray datas = null;
                                if (!js.getString("data").equals("null")) {
                                    String str = js.getString("data");
                                    datas = js.getJSONArray("data");
//                                    str = str.substring(1, str.length() - 1);
//                                    Log.d("datas", "--:" + str);
//                                    datas = new ArrayList<String>(Arrays.asList(str.split(",")));
                                    Log.d("datas", datas.toString());
                                }
                                Set<String> barcodes = new HashSet<>();
                                for (ExpressInfos e : expressList) {
                                    barcodes.add(e.getBarcode());
                                }
                                if (datas != null) {
                                    int length = datas.length();
                                    for (int i = 0; i < length; i++) {
//                                        Log.d("datas", "str=" + str);
                                        JSONObject sub = new JSONObject(datas.getString(i));
                                        Log.d("datas", sub.toString());
                                        //barcode,company,location,code,deadline,state
//                                        if (!barcodes.contains(sub.getString("barcode"))) {
                                        temp.add(new ExpressInfos(sub.toString()));
//                                        }
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            return temp;
                        }

                        @Override
                        protected void onPostExecute(List<ExpressInfos> temp) {
                            if (temp != null) {
                                expressList.clear();
                                expressList.addAll(temp);
//                                Collections.sort(expressList);
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }.execute(body);
                }
            }).post();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart() {
        synchronize_express(url, phone);
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        switch (id) {
            case R.id.nav_me:
                startActivity(new Intent(DrawerLayout_OneActivity.this, MeActivity.class));
                break;
            case R.id.nav_about:
                startActivity(new Intent(DrawerLayout_OneActivity.this, AboutActivity.class));
                break;
            case R.id.nav_security:
                startActivity(new Intent(DrawerLayout_OneActivity.this, SecurityActivity.class));
                break;
            case R.id.nav_setting:
                startActivity(new Intent(DrawerLayout_OneActivity.this, SettingActivity.class));
                break;
            case R.id.nav_logout:
                AlertDialog.Builder dialog = new AlertDialog.Builder(DrawerLayout_OneActivity.this);
                dialog.setIcon(R.drawable.ic_warning)
                        .setTitle("提醒")
                        .setMessage("是否注销当前用户？");
                dialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        logout();
                    }
                });
                dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialog.show();
                break;
            default:
                break;
        }
        return true;
    }

    private void logout() {
        Storage.getInstance(DrawerLayout_OneActivity.this).clearTokenCache();
        String url_logout = getResources().getString(R.string.str_server_url) + "/logout";
        try {
            new MyHttpClient(url_logout
                    , new JSONObject().put("Content-Type", "application/json;charset=utf-8")
                    , new JSONObject().put("phone", phone)
                    , new MyHttpClient.ResponseListener() {
                @Override
                public void onResponse(String body, JSONObject headers) {

                }
            }).post();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        startActivity(new Intent(DrawerLayout_OneActivity.this, LoginActivity.class));
        finish();
    }


    public MessageReceiver mMessageReceiver;
    public static String ACTION_INTENT_RECEIVER = "com.hefvcjm.expresshelper.NEW_EXPRESS_RECEIVER";

    /**
     * 动态注册广播
     */
    public void registerMessageReceiver() {
        mMessageReceiver = new MessageReceiver();
        IntentFilter filter = new IntentFilter();

        filter.addAction(ACTION_INTENT_RECEIVER);
        registerReceiver(mMessageReceiver, filter);
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (intent.getAction().equals(ACTION_INTENT_RECEIVER)) {
                Log.d("test_update", intent.getStringExtra("message"));
                synchronize_express(url, phone);
            }
        }

    }
}
