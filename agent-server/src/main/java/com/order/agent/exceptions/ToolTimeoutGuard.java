package com.order.agent.exceptions;

/**
 * 工具执行超时守卫：把同步工具调用包一层超时控制。
 */
public final class ToolTimeoutGuard {

    private ToolTimeoutGuard() {
    }

    /**
     * 在限时内执行工具任务；超时即中止并抛出 {@link ToolTimeoutException}。
     */
    public static <T> T run(String toolName, long timeoutMs, java.util.concurrent.Callable<T> task) {
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "tool-exec-" + toolName);
                    t.setDaemon(true);
                    return t;
                });
        try {
            java.util.concurrent.Future<T> future = executor.submit(task);
            try {
                return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw new ToolTimeoutException(toolName, timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ToolTimeoutException(toolName, timeoutMs);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("工具 " + toolName + " 执行失败：" + cause.getMessage(), cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
