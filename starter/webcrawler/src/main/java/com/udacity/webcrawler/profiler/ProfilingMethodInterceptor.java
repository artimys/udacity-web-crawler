package com.udacity.webcrawler.profiler;


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object target;
  private final ProfilingState state;

  // DONE: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, ProfilingState state, Object target) {
    this.clock = Objects.requireNonNull(clock);
    this.state = state;
    this.target = target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // DONE: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.

    // FIXME - look into:
    // Think carefully about how the proxy should behave for the
    // java.lang.Object#equals(Object) method.
    // Reading the InvocationHandler documentation will be very helpful.

    Object result;
    // Method has @profiled annotation
    boolean methodIsProfiled = method.getAnnotation(Profiled.class) != null;
    Instant startTime = null;

    // Start time if @Profiled present
    if (methodIsProfiled) {
      startTime = this.clock.instant();
    }

    try {
      // Run method before finally block runs
      result = method.invoke(this.target, args);
      return result;

    } catch (InvocationTargetException e) {
      throw e.getTargetException(); // Proxy to throw same error
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } finally {

      if (methodIsProfiled) {
        // Stop time
        Instant endTime = this.clock.instant();
        Duration profileDuration = Duration.between(startTime, endTime);

        // Record profile
        this.state.record(this.target.getClass(), method, profileDuration);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProfilingMethodInterceptor that = (ProfilingMethodInterceptor) o;

    return Objects.equals(this.clock, that.clock)
            && Objects.equals(this.target, that.target)
            && Objects.equals(this.state, that.state);
  }
}
