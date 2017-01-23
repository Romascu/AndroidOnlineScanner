
package info.guardianproject.checkey;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class MainActivity extends ActionBarActivity {
    private final String TAG = "MainActivity";

    private static int selectedItem = -1;
    private AppListFragment appListFragment = null;

    AdView mAdView;
    private InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)        // All emulators
                .addTestDevice("E9DEB34031182776A4E765DCEF19F10D")  // My phone
                .build();
        mAdView.loadAd(adRequest);

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-6887589184636373/7616095642");
        AdRequest adRequestInterstial = new AdRequest.Builder()
                .addTestDevice("E9DEB34031182776A4E765DCEF19F10D")
                .build();
        mInterstitialAd.loadAd(adRequestInterstial);

//listner for adClosed
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                AdRequest adRequest = new AdRequest.Builder()
                        .addTestDevice("E9DEB34031182776A4E765DCEF19F10D")
                        .build();
                mInterstitialAd.loadAd(adRequest);
            }
        });


    }

    @Override
    protected void onPause() {
        mAdView.pause();
        super.onPause();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (appListFragment == null)
            appListFragment = (AppListFragment) getSupportFragmentManager().findFragmentById(
                    R.id.fragment_app_list);
        if(mAdView!=null){  // Check if Adview is not null in case of fist time load.
            mAdView.resume();}

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ListAdapter adapter = appListFragment.getListAdapter();
        AppEntry appEntry = (AppEntry) adapter.getItem(selectedItem);
        if (appEntry == null) {
            Toast.makeText(this, R.string.error_no_app_entry, Toast.LENGTH_SHORT).show();
            return true;
        }
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, appEntry.getLabel());
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.details:
                showDetailView(appEntry, intent);
                return true;
            case R.id.generate_pin:
                generatePin(appEntry, intent);
                return true;
            case R.id.save:
                saveCertificate(appEntry, intent);
                return true;
            case R.id.virustotal:
                virustotal(appEntry, intent);
                return true;
            case R.id.by_apk_hash:
                byApkHash(appEntry, intent);
                return true;
            case R.id.by_package_name:
                byPackageName(appEntry, intent);
                return true;
            case R.id.by_signing_certificate:
                bySigningCertificate(appEntry, intent);
                return true;
            case R.id.action_settings:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static void showCertificateInfo(Activity activity, String packageName) {
        X509Certificate[] certs = Utils.getX509Certificates(activity, packageName);
        if (certs == null || certs.length < 1)
            return;
        /*
         * for now, just support the first cert since that is far and away the
         * most common
         */
        X509Certificate cert = certs[0];

        PublicKey publickey = cert.getPublicKey();
        int size;
        if (publickey.getAlgorithm().equals("RSA"))
            size = ((RSAPublicKey) publickey).getModulus().bitLength();
        else
            size = publickey.getEncoded().length * 7; // bad estimate

        TextView algorithm = (TextView) activity.findViewById(R.id.key_type);
        algorithm.setText(publickey.getAlgorithm() + " " + String.valueOf(size) + "bit");
        TextView keySize = (TextView) activity.findViewById(R.id.signature_type);
        keySize.setText(cert.getSigAlgName());
        TextView version = (TextView) activity.findViewById(R.id.version);
        version.setText(String.valueOf(cert.getVersion()));

        TextView issuerdn = (TextView) activity.findViewById(R.id.issuerdn);
        issuerdn.setText(cert.getIssuerDN().getName());
        TextView subjectdn = (TextView) activity.findViewById(R.id.subjectdn);
        subjectdn.setText(cert.getSubjectDN().getName());
        TextView serial = (TextView) activity.findViewById(R.id.serial);
        serial.setText(cert.getSerialNumber().toString(16));
        TextView created = (TextView) activity.findViewById(R.id.created);
        created.setText(cert.getNotBefore().toLocaleString());
        TextView expires = (TextView) activity.findViewById(R.id.expires);
        expires.setText(cert.getNotAfter().toLocaleString());

        TextView md5 = (TextView) activity.findViewById(R.id.MD5);
        md5.setText(Utils.getCertificateFingerprint(cert, "MD5").toLowerCase(Locale.ENGLISH));
        TextView sha1 = (TextView) activity.findViewById(R.id.sha1);
        sha1.setText(Utils.getCertificateFingerprint(cert, "SHA1").toLowerCase(Locale.ENGLISH));
        TextView sha256 = (TextView) activity.findViewById(R.id.sha256);
        sha256.setText(Utils.getCertificateFingerprint(cert, "SHA-256").toLowerCase(Locale.ENGLISH));
    }

    @SuppressLint("WorldReadableFiles")
    private void saveCertificate(AppEntry appEntry, Intent intent) {
        String packageName = appEntry.getPackageName();
        try {
            for (X509Certificate x509 : Utils.getX509Certificates(this, packageName)) {
                String fileName = packageName + ".cer";
                @SuppressWarnings("deprecation")
                final FileOutputStream os = openFileOutput(fileName,
                        Context.MODE_WORLD_READABLE);
                os.write(x509.getEncoded());
                os.close();

                String subject = packageName + " - " + x509.getIssuerDN().getName()
                        + " - " + x509.getNotAfter();
                Uri uri = Uri.fromFile(getFileStreamPath(fileName));
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pkix-cert");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.putExtra(Intent.EXTRA_TITLE, subject);
                i.putExtra(Intent.EXTRA_SUBJECT, subject);
                startActivity(Intent.createChooser(i, getString(R.string.save_cert_using)));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    private void generatePin(AppEntry appEntry, Intent intent) {
        String packageName = appEntry.getPackageName();
        try {
            X509Certificate[] certs = Utils.getX509Certificates(this, packageName);
            Properties prop = new Properties();
            prop.load(new StringBufferInputStream(certs[0].getSubjectDN().getName()
                    .replaceAll(",", "\n")));
            prop.list(System.out);
            String name;
            if (prop.containsKey("OU") && prop.containsKey("O"))
                name = (String) prop.get("OU") + prop.get("O");
            else if (prop.containsKey("O"))
                name = (String) prop.get("O");
            else if (prop.containsKey("OU"))
                name = (String) prop.get("OU");
            else if (prop.containsKey("CN"))
                name = (String) prop.get("CN");
            else
                name = "Unknown";
            name = name.replaceAll("[^A-Za-z0-9]", "");
            String fileName = name + "Pin.java";

            final FileOutputStream os = openFileOutput(fileName,
                    Context.MODE_WORLD_READABLE);
            os.write(("\npackage " + packageName + ";\n\n"
                    + "import info.guardianproject.trustedintents.ApkSignaturePin;\n\n"
                    + "public final class "
                    + name
                    + "Pin extends ApkSignaturePin {\n\n"
                    + "public " + name + "Pin() {\n"
                    + "\t\tfingerprints = new String[] {\n").getBytes());
            for (X509Certificate x509 : certs) {
                os.write(("\t\t\t\"" + Utils.getCertificateFingerprint(x509, "SHA-256")
                        .toLowerCase(Locale.ENGLISH) + "\",\n").getBytes());
            }
            os.write(("\t\t};\n" + "\t\tcertificates = new byte[][] {\n").getBytes());
            for (X509Certificate x509 : certs) {
                Log.i("AppListFragment", "subjectdn: " + x509.getSubjectDN().getName());
                os.write(Arrays.toString(x509.getEncoded())
                        .replaceFirst("\\[", "{").replaceFirst("\\]", "},")
                        .getBytes());
            }
            os.write("\t\t};\n\t}\n}\n".getBytes());
            os.close();
            Log.i("AppListFragment", "wrote " + fileName);

            String subject = packageName + " - " + certs[0].getIssuerDN().getName()
                    + " - " + certs[0].getNotAfter();
            Uri uri = Uri.fromFile(getFileStreamPath(fileName));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/x-java-source");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_TITLE, subject);
            i.putExtra(Intent.EXTRA_SUBJECT, subject);
            startActivity(Intent.createChooser(i, getString(R.string.save_cert_using)));

        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showDetailView(AppEntry appEntry, Intent intent) {

        intent.setClass(this, DetailViewActivity.class);
        intent.putExtra(Intent.EXTRA_SUBJECT, appEntry.getPackageName());
        startActivity(intent);
        mInterstitialAd.show();
    }

    private void virustotal(AppEntry appEntry, Intent intent) {
        String urlString = "https://www.virustotal.com/en/file/"
                + Utils.getBinaryHash(appEntry.getApkFile(), "sha256") + "/analysis/";
        intent.setData(Uri.parse(urlString));
        intent.putExtra(Intent.EXTRA_TITLE, R.string.virustotal);
        startActivity(intent);
        mInterstitialAd.show();
    }

    private void byApkHash(AppEntry appEntry, Intent intent) {
        String urlString = "https://androidobservatory.org/?searchby=binhash&q="
                + Utils.getBinaryHash(appEntry.getApkFile(), "sha1");
        intent.setData(Uri.parse(urlString));
        intent.putExtra(Intent.EXTRA_TITLE, R.string.by_apk_hash);
        startActivity(intent);
        mInterstitialAd.show();
    }

    private void byPackageName(AppEntry appEntry, Intent intent) {
        String urlString = "https://androidobservatory.org/?searchby=pkg&q="
                + appEntry.getPackageName();
        intent.setData(Uri.parse(urlString));
        intent.putExtra(Intent.EXTRA_TITLE, R.string.by_package_name);
        startActivity(intent);
        mInterstitialAd.show();
    }

    private void bySigningCertificate(AppEntry appEntry, Intent intent) {
        String sha1 = null;
        try {
            sha1 = Utils.getCertificateFingerprint(appEntry.getApkFile(), "sha1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(sha1)) {
            Toast.makeText(this, R.string.error_cannot_make_fingerprint, Toast.LENGTH_LONG).show();
        } else {
            intent.setData(Uri.parse("https://androidobservatory.org/?searchby=certhash&q=" + sha1));
            intent.putExtra(Intent.EXTRA_TITLE, R.string.by_signing_certificate);
            startActivity(intent);
        }
        mInterstitialAd.show();
    }

    public static class AppListFragment extends ListFragment implements
            LoaderCallbacks<List<AppEntry>> {

        private AppListAdapter adapter;
        private ListView listView;
        private static final String STATE_CHECKED = "info.guardianproject.checkey.STATE_CHECKED";
        WebView androidObservatoryView;

        public AppListFragment() {
            super();
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            setEmptyText(getString(R.string.no_applications_found));

            adapter = new AppListAdapter(getActivity());
            setListAdapter(adapter);
            setListShown(false);

            listView = getListView();
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

            if (savedInstanceState != null) {
                int position = savedInstanceState.getInt(STATE_CHECKED, -1);
                if (position > -1) {
                    listView.setItemChecked(position, true);
                }
            }

            // Prepare the loader
            // either reconnect with an existing one or start a new one
            getLoaderManager().initLoader(0, null, this);
        }

        @Override
        public void onSaveInstanceState(Bundle savedInstanceState) {
            super.onSaveInstanceState(savedInstanceState);
            savedInstanceState.putInt(STATE_CHECKED, selectedItem);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            // Start the CAB using the ActionMode.Callback defined above
            ActionBarActivity activity = (ActionBarActivity) getActivity();
            selectedItem = position;
            AppEntry appEntry = (AppEntry) adapter.getItem(selectedItem);
            showCertificateInfo(activity, appEntry.getPackageName());
        }

        @Override
        public Loader<List<AppEntry>> onCreateLoader(int id, Bundle args) {
            // This is called when a new loader needs to be created.
            // This sample only has one Loader with no arguments, so it is
            // simple.
            return new AppListLoader(getActivity());
        }

        @Override
        public void onLoadFinished(Loader<List<AppEntry>> loader, List<AppEntry> data) {
            adapter.setData(data);

            // The list should now be shown
            if (isResumed()) {
                setListShown(true);
            } else {
                setListShownNoAnimation(true);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<AppEntry>> loader) {
            // Clear the data in the adapter
            adapter.setData(null);
        }
    }
}
