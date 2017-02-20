package com.lzj.simpledownload;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @BindView(R.id.et_url)
    EditText etUrl;
    @BindView(R.id.et_num)
    EditText etNum;
    @BindView(R.id.start)
    Button start;
    @BindView(R.id.ll_pb)
    LinearLayout llPb;

    private String url;
    private int threadCount;
    private int runningCount;

    private List<ProgressBar> mPbs;
    private int progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mPbs = new ArrayList<>();
    }

    @OnClick(R.id.start)
    public void onClick() {
        Toast.makeText(this, "开始", Toast.LENGTH_SHORT).show();
        threadCount = Integer.valueOf(etNum.getText().toString().trim());
        url = etUrl.getText().toString().trim();
        runningCount = threadCount;
        clearData();
        for (int i = 0; i < threadCount; i++) {
            ProgressBar progressBar = (ProgressBar) View.inflate(this, R.layout.pb, null);
            llPb.addView(progressBar);
            mPbs.add(progressBar);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL downLoadUrl = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) downLoadUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setReadTimeout(5000);
                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        int contentLength = connection.getContentLength();
                        RandomAccessFile accessFile = new RandomAccessFile(getFileName(url), "rw");
                        accessFile.setLength(contentLength);
                        int blochSize = contentLength / threadCount;
                        for (int i = 0; i < threadCount; i++) {
                            int startIndex = i * blochSize;
                            int endIndex = (i + 1) * blochSize;
                            if (i == threadCount - 1) {
                                endIndex = contentLength - 1;
                            }
                            Thread thread = new DownLoadThread(i, startIndex, endIndex);
                            thread.start();
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.toString());
                    e.printStackTrace();
                }
            }
        }).start();

    }

    /**
     * 清空历史数据
     */
    private void clearData() {
        llPb.removeAllViews();
        mPbs.clear();
        File f = new File(getFileName(url));
        if (f.exists()) {
            f.delete();
        }
    }

    class DownLoadThread extends Thread {
        private int threadId;
        private int startIndex;
        private int endIndex;
        private int mPbMax;

        public DownLoadThread(int threadId, int startIndex, int endIndex) {
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public void run() {
            super.run();
            mPbMax = endIndex - startIndex;
            try {
                URL donwLoadUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) donwLoadUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);
                File caches = new File(getFileName(url) + "_" + threadId + ".txt");
                if (caches.exists() && caches.length() > 0) {
                    FileInputStream inputStream = new FileInputStream(caches);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    progress = Integer.valueOf(reader.readLine()) - startIndex;
                    startIndex = Integer.valueOf(reader.readLine());
                    reader.close();
                }
                int responseCode = connection.getResponseCode();
                if (responseCode == 206) {
                    InputStream inputStream = connection.getInputStream();
                    RandomAccessFile accessFile = new RandomAccessFile(getFileName(url), "rw");
                    accessFile.seek(startIndex);
                    int len = 0;
                    int total = 0;
                    byte[] buf = new byte[1024 * 1024];
                    while ((len = inputStream.read(buf)) != -1) {
                        total += len;
                        RandomAccessFile cacheFile = new RandomAccessFile(getFileName(url) + "_" + threadId + ".txt", "rwd");
                        cacheFile.write(String.valueOf(total + startIndex).getBytes());
                        cacheFile.close();
                        accessFile.write(buf, 0, len);
                        mPbs.get(threadId).setMax(mPbMax);
                        mPbs.get(threadId).setProgress(total + progress);
                    }
                    accessFile.close();
                    inputStream.close();
                    synchronized (DownLoadThread.class) {
                        runningCount--;
                        if (runningCount == 0) {
                            for (int i = 0; i < threadCount; i++) {
                                File cache = new File(getFileName(url) + "_" + i + ".txt");
                                if (cache.exists()) {
                                    cache.delete();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getFileName(String url) {
        int position = url.lastIndexOf("/") + 1;
        String path = Environment.getExternalStorageDirectory().getPath();
        String sdPath = path + "/" + url.substring(position);
        return sdPath;
    }

}
