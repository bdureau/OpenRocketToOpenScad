package orkToScad;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {
    public static void writeFile(String fileName, String fileContent) {
        try (PrintWriter writer = new PrintWriter(fileName, "UTF-8")) {
            writer.println(fileContent);
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static void unzip(String zipFilePath, String destDir) {
        File dir = new File(destDir);
        if (!dir.exists())
            dir.mkdirs();
        try (FileInputStream fis = new FileInputStream(zipFilePath);
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println("Unzipping to " + newFile.getAbsolutePath());
                new File(newFile.getParent()).mkdirs();
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
