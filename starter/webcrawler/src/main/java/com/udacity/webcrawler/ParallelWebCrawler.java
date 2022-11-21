package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;

  // Adding missing fields from SequentialWebCrawler.java
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;

  @Inject
  public PageParserFactory parserFactory;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @TargetParallelism int threadCount,
      @MaxDepth int maxDepth,
      @IgnoredUrls List<Pattern> ignoredUrls) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.maxDepth = maxDepth;
    this.ignoredUrls = ignoredUrls;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);

    // Switched to synchronization
//    Map<String, Integer> counts = new HashMap<>();
//    Set<String> visitedUrls = new HashSet<>();
    // https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/package-summary.html
    ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();


    for (String url : startingUrls) {
      crawlInternalTask internalTask = new crawlInternalTask(url, deadline, this.maxDepth, counts, visitedUrls);
      pool.invoke(internalTask);
    }

    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }






  public class crawlInternalTask extends RecursiveAction {
    private final String url;
    private final Instant deadline;
    private final int maxDepth;

    // arty - Sync both objects below
    // https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/package-summary.html
    private final ConcurrentHashMap<String, Integer> counts;
    private final ConcurrentSkipListSet<String> visitedUrls;

    // this.clock and this.ignoredUrls are available globally;
    // If this class is moved to its own file, pass variables to constructor.
    // private final Clock clock;
    // private final List<Pattern> ignoredUrls;

    public crawlInternalTask(String url, Instant deadline, int maxDepth, ConcurrentHashMap<String, Integer> counts, ConcurrentSkipListSet<String> visitedUrls) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
    }

    @Override
    protected void compute() {
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }

      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }

//      if (!visitedUrls.add(url)) {
//        return;
//      }
      // Replaced legacy code with code above
      if (visitedUrls.contains(url)) {
        return;
      }
      visitedUrls.add(url);



      PageParser.Result result = parserFactory.get(url).parse();

      for (ConcurrentHashMap.Entry<String, Integer> e : result.getWordCounts().entrySet()) {

        counts.compute(e.getKey(),
                      (k,v) -> (v == null ? e.getValue() : e.getValue() + v)
        );

        // If condition below is not thread safe, replaced all lines with .compute above
        //if (counts.containsKey(e.getKey())) {
        //  counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
        //} else {
        //  counts.put(e.getKey(), e.getValue());
        //}
      }

      List<crawlInternalTask> subTasks = new ArrayList<>();
      for (String link : result.getLinks()) {
        subTasks.add(new crawlInternalTask(link, deadline, maxDepth -1, counts, visitedUrls));
      }
      invokeAll(subTasks);

    }
  }
}
