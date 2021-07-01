package org.mkonchady.mytripoo.files;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Environment;

import org.mkonchady.mytripoo.Constants;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileDialog {
    private static final String PARENT_DIR = "..";
    //private final String TAG = getClass().getName();
    private boolean showing;
    private String[] fileList;
    private final Activity activity;
    private String[] fileEndsWith;
    private File currentPath;
    public interface FileSelectedListener {
        void fileSelected(File file);
    }
    private final ListenerList<FileSelectedListener> fileListenerList = new ListenerList<>();

    /**
     * @param activity Import file activity
     * @param path Import file path
     */
    public FileDialog(Activity activity, File path, String[] suffixes) {
        this.activity = activity;
        if (!path.exists())
            path = Environment.getExternalStorageDirectory();
        if (Constants.postAndroid10)
            path = activity.getExternalFilesDir("");

        setFileEndsWith(suffixes);
        loadFileList(path);
        showing = true;
    }

    /**
     * @return file dialog
     */
    public Dialog createFileDialog() {
        Dialog dialog;
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        DialogInterface.OnClickListener onClickListener= new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String fileChosen = fileList[which];
                File chosenFile = getChosenFile(fileChosen);
                if (chosenFile.isDirectory()) {
                    loadFileList(chosenFile);
        //            dialog.cancel();
        //            dialog.dismiss();
                    showDialog();
                } else fireFileSelectedEvent(chosenFile);
            }
        };
        DialogInterface.OnCancelListener onCancelListener = new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                showing = false;
            }
        };

        builder.setOnCancelListener(onCancelListener);
        builder.setTitle(currentPath.getPath());
        builder.setItems(fileList, onClickListener);
        dialog = builder.show();
        return dialog;
    }


    public void addFileListener(FileSelectedListener listener) {
        fileListenerList.add(listener);
    }

    /**
     * Show file dialog
     */
    public void showDialog() {
        createFileDialog().show();
    }

    private void fireFileSelectedEvent(final File file) {
        fileListenerList.fireEvent(new ListenerList.FireHandler<FileSelectedListener>() {
            public void fireEvent(FileSelectedListener listener) {
                listener.fileSelected(file);
            }
        });
    }

    private void loadFileList(File path) {
        this.currentPath = path;
        ArrayList<String> runningList = new ArrayList<>();
        if (path.exists()) {
            if (path.getParentFile() != null) runningList.add(PARENT_DIR);
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    if (!sel.canRead())
                        return false;
                    else {
                        boolean endsWith = false;
                        for (String suffix: fileEndsWith)
                            if (filename.toLowerCase().endsWith(suffix) )
                                endsWith = true;
                        return endsWith || sel.isDirectory();
                    }
                }
            };
            String[] fileList1 = path.list(filter);
            if (fileList1 != null)
                Collections.addAll(runningList, fileList1);
        }

        // Sorting
        Collections.sort(runningList, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return  s1.compareTo(s2);
            }
        });
        fileList = runningList.toArray(new String[runningList.size()]);

    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR))
            return currentPath.getParentFile();
        return new File(currentPath, fileChosen);
    }

    public void setFileEndsWith(String[] fileEndsWith) {
        this.fileEndsWith = new String[fileEndsWith.length];
        for (int i = 0; i < fileEndsWith.length; i++) {
            this.fileEndsWith[i] = fileEndsWith[i].toLowerCase();
        }
    }

    public void setShowing(boolean showing) {
        this.showing = showing;
    }
    public boolean isShowing() {
        return showing;
    }

}

class ListenerList<L> {
    private final List<L> listenerList = new ArrayList<>();

    public interface FireHandler<L> {
        void fireEvent(L listener);
    }

    public void add(L listener) {
        listenerList.add(listener);
    }

    public void fireEvent(FireHandler<L> fireHandler) {
        List<L> copy = new ArrayList<>(listenerList);
        for (L l : copy) {
            fireHandler.fireEvent(l);
        }
    }
}