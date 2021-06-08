package org.mkonchady.mytripoo.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import org.mkonchady.mytripoo.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// file utilities
public final class UtilsFile {

    private static final String TAG = "UtiltiesFile";
    private UtilsFile() {
        throw new AssertionError();
    }

    public static String getFileSuffix(String fileName) {
        String suffix = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0)  suffix = fileName.substring(i + 1);
        return suffix;
    }

    // force a scan of the files, so that it will show up in mtp
    public static void forceIndex(Context context, String filename) {
        //String[] indexFiles = new String[] { getAbsoluteFilePath(context, TRIP_FILE) };
        String[] indexFiles = new String[] { new File(context.getExternalFilesDir(null), filename).toString() };
        MediaScannerConnection.scanFile(context, indexFiles, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                    }
                }
        );
    }

    // get the full filename
    public static String getFileName(Context context, String filename) {
        if (filename.length() == 0) return "";
        String[] indexFiles = new String[] { new File(context.getExternalFilesDir(null), filename).toString() };
        if (indexFiles.length > 0) return indexFiles[0];
        return "";
    }


    public static void zip(Context context, String[] files, String zipFile) {
        final int BUFFER = 2048;
        try  {
            BufferedInputStream origin;
            FileOutputStream dest = openOutputFile(context, zipFile);
            Logger.d(TAG, "Opened output file");
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            byte[] data = new byte[BUFFER];
            for (String file: files) {
                FileInputStream fi = new FileInputStream(file);
                Logger.d(TAG, "Adding file: " + file);
                origin = new BufferedInputStream(fi, BUFFER);
                ZipEntry entry = new ZipEntry(file.substring(file.lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER)) != -1)
                    out.write(data, 0, count);
                origin.close();
            }
            out.close();
            forceIndex(context, zipFile);

        } catch(IOException ie) {
            Logger.e(TAG, "File error: " + ie.getMessage());
        }

    }

    // create a new file if it does not exist
    public static FileOutputStream openOutputFile(Context context, String filename) throws IOException {
        FileOutputStream fos;
        File file = new File(context.getExternalFilesDir(null), filename);
        if (!file.exists())
            if (!file.createNewFile()) throw new IOException();
        fos = new FileOutputStream(file, false);
        return fos;
    }


    public static ArrayList<String> unzip(Context context, String zipFile) {
        ArrayList<String> files = new ArrayList<>();
        try  {
            FileInputStream fin = new FileInputStream(zipFile);
            ZipInputStream zin = new ZipInputStream(fin);
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                Logger.d(TAG, "Unzipping " + ze.getName());
                FileOutputStream fout = openOutputFile(context, ze.getName());
                BufferedOutputStream bufout = new BufferedOutputStream(fout);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = zin.read(buffer)) != -1) {
                    bufout.write(buffer, 0, read);
                }
                bufout.close();
                zin.closeEntry();
                fout.close();
                files.add(ze.getName());
                forceIndex(context, ze.getName());
            }
            zin.close();
        } catch(IOException ie) {
            Logger.e(TAG, "unzip error: " + ie.getMessage());
        }
        return files;

    }

    // read the contents of a file into a string
    static String slurpFile(Context context, String filename) {
        File file = new File(context.getExternalFilesDir(null), filename);
        StringBuilder fileContents = new StringBuilder((int) file.length());
        Scanner scanner = null;
        try {
            scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine() + " ");
            }
            return fileContents.toString();
        }catch (FileNotFoundException fe) {
            return "";
        } finally {
            scanner.close();
        }
    }

    // get the length of a file
    static int getFileLength(Context context, String filename)  {
        File file = new File(context.getExternalFilesDir(null), filename);
        return (int) file.length();
    }

    static String removeLastChar(String inline,  char ch) {
        if (inline != null && inline.length() > 0 && inline.charAt(inline.length() - 1) == ch) {
            inline = inline.substring(0, inline.length() - 1);
        }
        return inline;
    }
}
