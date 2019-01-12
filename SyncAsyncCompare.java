package tutorial;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class SquareRpcService {
    static public int nConcurrentRequests = 10;

    static public ListeningExecutorService service = MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(nConcurrentRequests));

    static ListenableFuture<Integer> squareRPC(Integer i) {
        return service.submit(()-> {
            Thread.sleep(1000);
            return i * i;
        });
    }
}

public class SyncAsyncCompare {

    static private Integer doCalcSync(Integer i) {
        long start = System.nanoTime() / 1000000000;
        try {
            Integer ret = SquareRpcService.squareRPC(i).get() + 1;
            long end = System.nanoTime() / 1000000000;
            System.out.printf("Finished sync calc on thread %d, result is %d, start time is %d, end time is %d s\n",
                    Thread.currentThread().getId(), ret, start, end);
            return ret;
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    static private ListenableFuture<Integer> doCalcAsync(Integer i, Executor executor) {
        long start = System.nanoTime() / 1000000000;
        ListenableFuture<Integer> f = SquareRpcService.squareRPC(i);
        return Futures.transform(f, d -> {
            long end = System.nanoTime() / 1000000000;
            int ret = d + 1;
            System.out.printf("Finished async calc on thread %d, result is %d, start time is %d, end time is %d s\n",
                    Thread.currentThread().getId(), ret, start, end);
            return ret;
            }, executor);
    }

    static public void main(String [] args) throws Exception {
        int nThreads = 1;
        Stopwatch stopwatch;
        ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(nThreads));

        stopwatch = Stopwatch.createStarted();
        List<Callable<Integer>> callables = new ArrayList<>();
        for (int i = 0; i < SquareRpcService.nConcurrentRequests; i++) {
            int num = i;
            callables.add(() -> doCalcSync(num));
        }
        List<Future<Integer>> ret1 = executorService.invokeAll(callables);

        for (int i = 0; i < SquareRpcService.nConcurrentRequests; i++) {
            ret1.get(i).get();
        }
        stopwatch.stop();
        System.out.printf("Sync Mode: total elapsed time %d ms\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        System.out.println();

        stopwatch = Stopwatch.createStarted();
        List<ListenableFuture<Integer>> ret2 = new ArrayList<>();
        for (int i = 0; i < SquareRpcService.nConcurrentRequests; i++) {
            ret2.add(doCalcAsync(i, executorService));
        }
        for (int i = 0; i < SquareRpcService.nConcurrentRequests; i++) {
            ret2.get(i).get();
        }

        stopwatch.stop();
        System.out.printf("Async Mode: total elapsed time %d ms\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        SquareRpcService.service.shutdown();
        executorService.shutdown();
    }
}

