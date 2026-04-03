package com.filemanager.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class EbookConverterService {
    
    @Value("${app.scripts.path:./scripts}")
    private String scriptsPath;
    
    private static final long CONVERSION_TIMEOUT = 60;
    
    public File convertToEpub(InputStream inputStream, String originalFilename) throws Exception {
        String ext = getFileExtension(originalFilename).toLowerCase();
        
        if (!ext.equals("mobi") && !ext.equals("azw3")) {
            throw new IllegalArgumentException("Unsupported format: " + ext);
        }
        
        Path tempDir = Files.createTempDirectory("ebook_convert_");
        Path inputFile = tempDir.resolve("input." + ext);
        Path outputFile = tempDir.resolve("output.epub");
        
        try {
            Files.copy(inputStream, inputFile, StandardCopyOption.REPLACE_EXISTING);
            
            String scriptPath = Paths.get(scriptsPath, "ebook_converter.py").toAbsolutePath().toString();
            
            ProcessBuilder pb = new ProcessBuilder(
                "python3",
                scriptPath,
                inputFile.toAbsolutePath().toString(),
                outputFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(CONVERSION_TIMEOUT, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("转换超时");
            }
            
            if (process.exitValue() != 0) {
                String errorMsg = output.toString();
                if (errorMsg.contains("Calibre")) {
                    throw new RuntimeException("AZW3格式需要安装Calibre进行转换。请运行: brew install --cask calibre");
                }
                throw new RuntimeException("转换失败: " + errorMsg);
            }
            
            if (!Files.exists(outputFile)) {
                throw new RuntimeException("输出文件未创建");
            }
            
            return outputFile.toFile();
            
        } finally {
            Files.deleteIfExists(inputFile);
            tempDir.toFile().deleteOnExit();
        }
    }
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
    
    public void cleanup(File file) {
        if (file != null && file.exists()) {
            file.delete();
            file.getParentFile().delete();
        }
    }
}