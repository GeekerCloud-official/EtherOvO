package com.geekera1n.etherovo; // 请确保包名一致

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class RootUtil {

    private static final String TAG = "RootCommand";

    /**
     * 执行一条root命令并返回其标准输出和错误输出。
     * @param command 要执行的命令
     * @return 返回命令的执行结果，包含stdout和stderr。
     */
    public static CommandResult executeRootCommand(String command) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Process process = null;
        DataOutputStream os = null;
        BufferedReader stdoutReader = null;
        BufferedReader stderrReader = null;

        try {
            process = Runtime.getRuntime().exec("su");
            // 这是修正后的行
            os = new DataOutputStream(process.getOutputStream());
            stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            Log.d(TAG, "Executing: " + command);
            os.writeBytes(command + "\n");
            os.flush();
            os.writeBytes("exit\n");
            os.flush();

            String line;
            while ((line = stdoutReader.readLine()) != null) {
                stdout.append(line).append("\n");
            }
            while ((line = stderrReader.readLine()) != null) {
                stderr.append(line).append("\n");
            }

            process.waitFor();

        } catch (Exception e) {
            Log.e(TAG, "Root command execution failed with exception", e);
            stderr.append("Exception: ").append(e.getMessage());
        } finally {
            try {
                if (os != null) os.close();
                if (stdoutReader != null) stdoutReader.close();
                if (stderrReader != null) stderrReader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }

        if (stdout.length() > 0) {
            Log.d(TAG, "STDOUT: \n" + stdout.toString().trim());
        }
        if (stderr.length() > 0) {
            Log.e(TAG, "STDERR: \n" + stderr.toString().trim());
        }

        return new CommandResult(stdout.toString(), stderr.toString());
    }

    /**
     * 一个简单的类，用于封装命令执行的结果。
     */
    public static class CommandResult {
        public final String stdout;
        public final String stderr;

        public CommandResult(String stdout, String stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean isSuccess() {
            return stderr == null || stderr.trim().isEmpty();
        }
    }
}