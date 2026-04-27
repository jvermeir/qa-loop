package com.example.demo.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileProcessor {

    // S2095: BufferedReader not in try-with-resources — leaks on exception
    public List<String> readLines(String filePath) {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            // S1166: exception swallowed — caller gets empty list silently
        }
        return lines;
    }

    // S2095: PrintWriter leaks if an exception is thrown after construction
    public void writeReport(String filePath, String content) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(filePath, true));
            writer.println(content);
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            // writer is never closed on the exception path
        }
    }

    // S2083: path traversal — user-controlled fileName bypasses /app/data/ prefix
    public String readFile(String fileName) {
        try {
            Path path = Paths.get("/app/data/" + fileName);
            return new String(Files.readAllBytes(path));
        } catch (IOException e) {
            return null; // S2259: callers risk NPE
        }
    }

    // S1854: dead store — `count` is assigned but never read
    public int countWords(String text) {
        int count = 0;
        String[] words = text.split(" ");
        count = words.length; // dead store: overwritten immediately below
        int result = 0;
        for (String word : words) {
            if (!word.isEmpty()) {
                result++;
            }
        }
        return result;
    }
}
