package com.xyoye.danmuplayer.ui.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.xyoye.danmuplayer.R;
import com.xyoye.danmuplayer.bean.Directory;
import com.xyoye.danmuplayer.database.DirectoryDao;
import com.xyoye.danmuplayer.database.SharedPreferencesHelper;
import com.xyoye.danmuplayer.ui.adpter.DirectoryAdapter;
import com.xyoye.danmuplayer.utils.FindVideoList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * update by xyy on 2018/3/3.
 */

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener,
        View.OnClickListener {
    final static int ADD_DIRECTORY = 1;

    @BindView(R.id.iv_add_folder)
    ImageView addDirectory;
    @BindView(R.id.iv_about_display)
    ImageView aboutDisplay;
    @BindView(R.id.lv_folder)
    ListView lvFolder;
    @BindView(R.id.swipeLayout)
    SwipeRefreshLayout swipeRefreshLayout;

    DirectoryDao ddao;
    DirectoryAdapter directoryAdapter;
    SharedPreferencesHelper mSharedHelper;

    List<Directory> systemDirectoryList;
    List<Directory> databaseDirectoryList;

    int searchWaitTime;
    boolean searchFileOver = false;
    boolean searchFileSuccess = true;
    boolean isFirstRun = true;
    private boolean waitExit = true;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler(){
        @Override
        public void handleMessage(android.os.Message msg){
            switch (msg.what){
                case 100:
                    FlashDatabase();
                    ShowDirectory();
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(MainActivity.this, "更新文件列表成功！！！", Toast.LENGTH_LONG).show();
                    break;
                case 101:
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(MainActivity.this, "更新文件列表失败！！！", Toast.LENGTH_LONG).show();
                    break;
                case 102:
                    ShowDirectory();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initView();

        initData();

        initListener();

        if (isFirstRun)
            getFileList();
    }

    /**
     * 初始化组件
     */
    public void initView(){
        addDirectory.setImageResource(R.drawable.ic_add_directory);
        aboutDisplay.setImageResource(R.drawable.ic_about_display);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_light, android.R.color.holo_red_light, android.R.color.holo_orange_light, android.R.color.holo_green_light);
        swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
        swipeRefreshLayout.setProgressViewEndTarget(true, 100);
    }

    /**
     * 初始化数据
     */
    public void initData(){
        SharedPreferencesHelper.init(this);
        mSharedHelper = SharedPreferencesHelper.getInstance();

        ddao = new DirectoryDao(MainActivity.this);
        databaseDirectoryList = new ArrayList<>();

        isFirstRun = mSharedHelper.getBoolean("isFirstRun", true);
        mSharedHelper.saveBoolean("isFirstRun", false);
    }

    /**
     * 初始化事件
     */
    public void initListener(){
        addDirectory.setOnClickListener(this);
        aboutDisplay.setOnClickListener(this);
        lvFolder.setOnItemClickListener(this);
        lvFolder.setOnItemLongClickListener(this);
        swipeRefreshLayout.setOnRefreshListener(this);
    }

    /**
     * 获取文件列表
     */
    public void getFileList(){
        new Thread(){
            @Override
            public void run(){
                //遍历系统文件,并录入数据库
                FlashFileList();
                searchWaitTime = 0;
                searchFileSuccess = true;
                while (!searchFileOver) {
                    try {
                        Thread.sleep(1000);
                        searchWaitTime++;
                        searchFileSuccess = true;
                        Log.i("FLASH","正在更新");
                        //五秒内未获取到文件列表，判断为获取失败
                        if (searchWaitTime>5){
                            searchFileSuccess = false;
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (searchFileSuccess){
                    Message message  = new Message();
                    message.what = 100;
                    handler.sendMessage(message);
                }else {
                    Message message  = new Message();
                    message.what = 101;
                    handler.sendMessage(message);
                }
            }
        }.start();
    }

    /**
     * 获取系统中video文件信息并更新到数据库中
     */
    public void FlashFileList(){
        searchFileOver = false;
        systemDirectoryList = new ArrayList<>();
        //获取系统中video文件地址以及video时长
        FindVideoList findVideoList = new FindVideoList();
        findVideoList.setQueryListener(new FindVideoList.QueryListener() {
            @Override
            public void onResult(List<Directory> mediaInfoList) {
                systemDirectoryList = mediaInfoList;

                if (systemDirectoryList.size()>0)
                {
                    for (int onefile=0;onefile<systemDirectoryList.size();onefile++)
                    {
                        //得到video时间
                        String[] get_video_name_array = systemDirectoryList.get(onefile).getdirectory_file_path().split("/");
                        String video_name = get_video_name_array[get_video_name_array.length-1];
                        int Suffix = video_name.lastIndexOf(".");
                        video_name = video_name.substring(0,Suffix);
                        //得到video所属文件夹
                        String video_directory = get_video_name_array[get_video_name_array.length-2];
                        //得到video地址
                        String video_path = systemDirectoryList.get(onefile).getdirectory_file_path();
                        //得到video时长
                        int video_time = (int)systemDirectoryList.get(onefile).getdirectory_file_time();

                        //判断数据库中是否已经存在该视频地址
                        boolean file_had = ddao.QueryFileHad(video_path);
                        if (!file_had){
                            //添加到数据库
                            ddao.insert(video_directory,video_name,video_path,video_time,"null");
                        }
                    }
                    searchFileOver = true;
                }
                else {
                    searchFileOver = true;
                }
            }
        });
        findVideoList.execute(MainActivity.this);
    }

    /**
     * 更新数据库信息
     */
    public void FlashDatabase(){
        List<String> directory_file_list = ddao.QueryAllFile();
        if (directory_file_list!=null&&directory_file_list.size()>0)
        {
            for (int i=0;i<directory_file_list.size();i++)
            {
                try{
                    File f=new File(directory_file_list.get(i));
                    if(!f.exists()){
                        ddao.deleteFile(directory_file_list.get(i));
                    }

                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 显示数据库中所有文件夹信息
     */
    public void ShowDirectory(){
        //查询数据库中所有文件夹，以及其中包含的视频数量
        List<String> directory_list =  ddao.QueryDirectorys();
        databaseDirectoryList = new ArrayList<>();
        if (directory_list!=null&&directory_list.size()>0)
        {
            for (int i=0;i<directory_list.size();i++)
            {
                List<String> directory_file_number_list = ddao.QueryFiles(directory_list.get(i));
                Directory directory = new Directory();
                directory.setdirectory_name(directory_list.get(i));
                directory.setdirectory_file_number(directory_file_number_list.size()+"");
                databaseDirectoryList.add(directory);
            }
        }

        directoryAdapter = new DirectoryAdapter(MainActivity.this,databaseDirectoryList);
        lvFolder.setAdapter(directoryAdapter);
    }

    /**
     * 展示关于信息
     */
    public void showAbout(){
        View about_dialog = View.inflate(MainActivity.this,R.layout.about_more,null);
        AlertDialog.Builder about_builder = new AlertDialog.Builder(MainActivity.this).setView(about_dialog);

        TextView bt_aboutPlayer = about_dialog.findViewById(R.id.about_player);
        bt_aboutPlayer.setOnClickListener(new android.view.View.OnClickListener(){
            @Override
            public void onClick(View v) {
                View dialog = View.inflate(MainActivity.this,R.layout.about_player,null);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this).setView(dialog);
                builder.show();
            }
        });

        TextView bt_aboutUse = about_dialog.findViewById(R.id.about_use);
        bt_aboutUse.setOnClickListener(new android.view.View.OnClickListener(){
            @Override
            public void onClick(View v) {
                View dialog = View.inflate(MainActivity.this,R.layout.about_use,null);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this).setView(dialog);
                builder.show();
            }
        });

        about_builder.show();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent open_video_list_activity = new Intent(MainActivity.this, VideoListActivity.class);
        open_video_list_activity.putExtra("title",databaseDirectoryList.get(position).getdirectory_name());
        startActivity(open_video_list_activity);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final String directory_name  = databaseDirectoryList.get(position).getdirectory_name();
        AlertDialog.Builder builder_dalete = new AlertDialog.Builder(this);
        builder_dalete.setTitle("确认删除此文件夹？").setView(null).setNegativeButton("取消", null);
        builder_dalete.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                ddao.deleteDirectory(directory_name);
                ShowDirectory();
                Toast.makeText(MainActivity.this,"删除成功！！！",Toast.LENGTH_LONG).show();
            }
        });
        builder_dalete.show();
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.iv_add_folder:
                Intent intent = new Intent(MainActivity.this, FolderChooserActivity.class);
                intent.putExtra("isFolderChooser", true);
                startActivityForResult(intent,ADD_DIRECTORY);
                break;
            case R.id.iv_about_display:
                showAbout();
                break;
        }
    }

    @Override
    public void onRefresh() {
        getFileList();
    }

    @Override
    public void onBackPressed() {
        if (waitExit) {
            waitExit = false;
            Toast.makeText(MainActivity.this,getString(R.string.press_to_exit), Toast.LENGTH_SHORT).show();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    waitExit = true;
                }
            }, 2000);
        } else {
            finish();
            System.exit(0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode){
                case ADD_DIRECTORY:
                    File folder = (File) data.getSerializableExtra("file_path");
                    String directory_path = folder.getAbsolutePath();
                    List<Directory> addFileList;
                    addFileList = new FindVideoList().getfilelist(MainActivity.this,directory_path);
                    if (addFileList.size()>0) {
                        for (int onefile = 0; onefile < addFileList.size(); onefile++) {
                            //得到video时间
                            String[] get_video_name_array = addFileList.get(onefile).getdirectory_file_path().split("/");
                            String video_name = get_video_name_array[get_video_name_array.length - 1];
                            int Suffix = video_name.lastIndexOf(".");
                            video_name = video_name.substring(0, Suffix);
                            //得到video所属文件夹
                            String video_directory = get_video_name_array[get_video_name_array.length - 2];
                            //得到video地址
                            String video_path = addFileList.get(onefile).getdirectory_file_path();
                            //得到video时长
                            int video_time = (int) addFileList.get(onefile).getdirectory_file_time();

                            //判断数据库中是否已经存在该视频地址
                            boolean file_had = ddao.QueryFileHad(video_path);
                            if (!file_had) {
                                //添加到数据库
                                ddao.insert(video_directory, video_name, video_path, video_time, "null");
                            }
                        }
                    }
                    Message add_file_over_message = new Message();
                    add_file_over_message.what = 102;
                    handler.sendMessage(add_file_over_message);
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FlashDatabase();
        ShowDirectory();
    }
}