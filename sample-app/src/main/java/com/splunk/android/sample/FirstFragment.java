package com.splunk.android.sample;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.fragment.NavHostFragment;

import com.splunk.android.sample.databinding.FragmentFirstBinding;
import com.splunk.rum.SplunkRum;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.apache.http.conn.ssl.SSLSocketFactory.SSL;

public class FirstFragment extends Fragment {

    private final ExecutorService backgrounder = Executors.newSingleThreadExecutor();
    private final MutableLiveData<String> httpResponse = new MutableLiveData<>();

    private FragmentFirstBinding binding;
    private OkHttpClient okHttpClient;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        okHttpClient = buildOkHttpClient();
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setFirstFragment(this);

        binding.buttonFirst.setOnClickListener(v ->
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment));

        binding.crash.setOnClickListener(v -> {
            throw new IllegalStateException("Crashing due to a bug!");
        });

        binding.httpMe.setOnClickListener(v -> backgrounder.submit(() -> makeCall("https://ssidhu.o11ystore.com/")));
        binding.httpMeBad.setOnClickListener(v -> backgrounder.submit(() -> makeCall("https://asdlfkjasd.asdfkjasdf.ifi")));
        binding.httpMeNotFound.setOnClickListener(v -> backgrounder.submit(() -> makeCall("https://ssidhu.o11ystore.com/foobarbaz")));
    }

    private void makeCall(String url) {
        Call call = okHttpClient.newCall(new Request.Builder().url(url).get().build());
        try (Response r = call.execute()) {
            int responseCode = r.code();
            httpResponse.postValue("" + responseCode);
        } catch (IOException e) {
            httpResponse.postValue("error");
        }
    }

    public LiveData<String> getHttpResponse() {
        return httpResponse;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @NonNull
    private OkHttpClient buildOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(SplunkRum.getInstance().createOkHttpRumInterceptor());
        try {
            // NOTE: This is really bad and dangerous. Don't ever do this in the real world.
            // it's only necessary because the demo endpoint uses a self-signed SSL cert.
            SSLContext sslContext = SSLContext.getInstance(SSL);
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            return builder
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(new AllowAllHostnameVerifier())
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    private static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                               String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                               String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };

}