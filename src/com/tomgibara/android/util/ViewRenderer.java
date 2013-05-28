package com.tomgibara.android.util;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
 
/**
 * <p>
 * This excellent class was borrowed from Tom Gibara at:
 * http://blog.tomgibara.com/post/7665158012/android-adapter-view-rendering
 * Modified only to add this attribution. Thanks!
 * </p>
 * 
 * <p>
 * Concrete extensions of this class are capable of rendering a multiplicity of
 * Views on background threads. Interrupting the rendering of a View to start a
 * new rendering is handled by this class. So too is rendering a view in
 * multiple passes (initial passes are automatically prioritized over subsequent
 * passes). Support for caching renderings is also available.
 * <p>
 * 
 * <p>
 * This class is mainly intended to be used within
 * {@link Adapter#getView(int, View, android.view.ViewGroup)} to handle all
 * aspects of the asynchronous rendering of the views within an associated
 * {@link AdapterView}.
 * </p>
 * 
 * <p>
 * Example: A simple scenario would be a {@link GridView} containing
 * {@link ImageView}s which are rendered by an instance of this class where
 * <code>Param<code> is {@link Uri} and <code>Render</code> is {@link Bitmap}
 * and the implementation loads bitmaps over HTTP for display in the view. The
 * {@link GridView} would invoke the {@link ViewRenderer} via an {@link Adapter}
 * that called the {@link #renderView(View, Object)} method on each call to its
 * {@link Adapter#getView(int, View, android.view.ViewGroup)} method. Such an
 * implementation might support two pass rendering, loading a low-res image on
 * the first pass before loading a high-res image later.
 * </p>
 * 
 * <p>
 * <strong>Note that this class requires a well defined equals method on the
 * class that satisfies Param.</strong>
 * </p>
 * 
 * @author Tom Gibara
 * 
 * @param <Param>
 *            the type of parameters that define renders
 * @param <Render>
 *            the type of renders that are applied to views
 */
 
public abstract class ViewRenderer<Param, Render> {
 
	// statics
	
	// convenience method for testing equality
	private static final boolean equal(Object a, Object b) {
        if (a == b) return true;
        if (a == null) return false;
        if (b == null) return false;
        return a.equals(b);
	}
	
	// shared default executor, should be good enough in the common case
	// and avoids thread proliferation in casual use
	private static Executor sDefaultExecutor = null;
 
	// ThreadPoolExecutor is broken for priority queues (see 6539720) so we need to patch it.
	// Also newTaskFor() was only introduced in API level 9, so we can't use that either
	// instead we patch the submit method directly
	private static class Executor extends ThreadPoolExecutor {
		
		public Executor(int threadCount) {
			// ThreadPoolExecutor can't be configured to grow the thread count without risking queue rejections
			// so we take the easy way out and clamp it with a fixed number of threads.
			// TODO introduce our own executor (or equivalent) that doesn't share this weakness
			// TODO our own blocking queue which stitched-together lists of the same priority would be much more efficient
			super(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>());
		}
		
		@Override
		public <T> Future<T> submit(Callable<T> task) {
			if (task == null) throw new NullPointerException();
			ComparableFuture<T> future = new ComparableFuture<T>(task);
			execute(future);
			return future;
		}
 
		@SuppressWarnings("unchecked")
		private static class ComparableFuture<T> extends FutureTask<T> implements Comparable<ComparableFuture<T>> {
 
			private final Comparable mComparable;
			
			public ComparableFuture(Callable<T> callable) {
				super(callable);
				mComparable = (Comparable) callable;
			}
			
			@Override
			public int compareTo(ComparableFuture<T> that) {
				return this.mComparable.compareTo(that.mComparable);
			}
			
		}
		
	}
	
	// wraps access to the default executor to allow for lazy creation
	private static synchronized Executor getDefaultExecutor() {
		return sDefaultExecutor == null ? sDefaultExecutor = new Executor(1) : sDefaultExecutor;
	}
 
	// fields
	
	private final Executor mExecutor;
	private final boolean mMayInterruptIfRunning;
	private final Handler mHandler;
	private final int mInheritedThreadPriority;
	private final int mPasses;
	
	private volatile long mNextOrder = 0L;
	private volatile boolean mStopping = false;
	
	private final Cache mCache;
	
	// constructors
 
	/**
	 * Constructs a new {@link ViewRenderer} object that coordinates concurrent
	 * background rendering for display to views. It is primarily designed for
	 * use with {@link AdapterView} based views. Supplying zero for the thread
	 * count will cause the {@link ViewRenderer} to use a single thread that
	 * can be shared among other instances.
	 * 
	 * @param maxThreadCount
	 *            the number of threads that will be created to support
	 *            background rendering, may be zero.
	 * @param passes
	 *            the number of rendering passes required to complete the
	 *            rendering required for a view
	 * @param mayInterruptIfRunning
	 *            whether background rendering tasks can be interrupted if they
	 *            become redundant
	 * @param cacheCapacity
	 *            the maximum number of render
	 * 
	 */
	
	public ViewRenderer(int threadCount, int passes, boolean mayInterruptIfRunning, int cacheCapacity) {
		if (threadCount < 0) throw new IllegalArgumentException("negative thread count");
		if (passes <= 0) throw new IllegalArgumentException("passes not positive");
		if (cacheCapacity < 0) throw new IllegalArgumentException("negative cache capacity");
		mExecutor = threadCount == 0 ? getDefaultExecutor() : new Executor(threadCount);
		mPasses = passes;
		mMayInterruptIfRunning = mayInterruptIfRunning;
		mCache = cacheCapacity == 0 ? null : new Cache(cacheCapacity);
		mHandler = new Handler();
		mInheritedThreadPriority = Process.getThreadPriority(Process.myTid());
	}
	
	// methods
	
	/**
	 * Causes a view to be rendered using the supplied parameters. The nature of
	 * the rendering performed is determined by the concrete subclass on which
	 * this method is called, but in all cases, the view is initialized (by a
	 * call to {@link #prepare(View, Object, int)} and a Render object created
	 * in {@link #render(Object, int)} (on a background thread) before being
	 * displayed (in a call to {@link #update(View, Object, int)}.
	 * 
	 * @param view
	 *            the view that will display the rendering
	 * @param param
	 *            the parameters that define the rendering
	 */
	
	public void renderView(View view, Param param) {
		if (mStopping) throw new IllegalStateException("stopped");
		//check the parameters
		if (view == null) throw new IllegalArgumentException("null view");
		
		// see if there's a tag which will tell us about past rendering on this view
		Task task = getTask(view);
		if (task != null) {
			if (equal(param, task.mParam)) {
				// a task has already done this (or will)
				// so there's nothing else to do
				return;
			} else {
				// try to cancel the existing task
				// this may save lots of work
				task.removeFromView(view);
			}
		}
		// try to obtain a task from the cache
		if (mCache == null) {
			task = null;
		} else {
			synchronized (mCache) {
				task = mCache.get(param);
			}
		}
		// create a task that will render and later update the view
		if (task == null) task = new Task(param);
		// associate the task with the view (and schedule it for rendering as necessary)
		task.assignToView(view);
	}
	
	/**
	 * Instructs this renderer that any cached renders should be purged. This
	 * method may be useful for dealing with low memory conditions or situations
	 * where parameter equality is no longer valid.
	 */
	
	public void clearCache() {
		mCache.clear();
	}
 
	/**
	 * Stops this renderer and makes it unusable for further rendering. A zero
	 * value for the timeout blocks indefinitely. A negative timeout value will
	 * cause the method to return immediately without waiting for background
	 * rendering operations to complete. Note that even if no timeout is set, or
	 * the timeout is exceeded, the renderer will not call
	 * {@link #update(View, Object, int)} or {@link #prepare(View, Object, int)}
	 * or otherwise modify a view at any time after the this method has been
	 * called.
	 * 
	 * @param timeout
	 *            the number of milliseconds for which to wait for rendering
	 *            operations to terminate
	 */
	
	public void stop(long timeout) throws InterruptedException {
		if (mStopping) return;
		mStopping = true;
		if (mExecutor != sDefaultExecutor) {
			mExecutor.shutdown();
			if (timeout == 0) {
				timeout = Long.MAX_VALUE;
			}
			if (timeout >= 0) {
				mExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
			}
		}
	}
 
	/**
	 * Stop the renderer without waiting for calls to
	 * {@link #render(Object, int)} to complete. This is a convenient way of
	 * calling {@link #stop(long)} without specifying a timeout.
	 */
	
	public void stop() {
		try {
			stop(-1L);
		} catch (InterruptedException e) {
			throw new IllegalStateException("Impossible: interrupted without waiting");
		}
	}
 
	/**
	 * The thread priority assigned to the specified rendering pass. The default
	 * implementation returns {@link Process#THREAD_PRIORITY_BACKGROUND} for
	 * every pass. 
	 * 
	 * @param pass the rendering pass for which the priority is being requested
	 * @return a thread priority
	 * @see Process
	 */
	
	protected int getThreadPriority(int pass) {
		return Process.THREAD_PRIORITY_BACKGROUND;
	}
	
	/**
	 * <p>
	 * Prepares a view for display in advance of being updated with its rendered
	 * content. A non-negative immediatePassHint indicates the update method
	 * will be called immediately after this call to prepare (this may enable
	 * some optimizations within the prepare method), otherwise the view will
	 * displayed to the visitor until the background rendering completes. This
	 * method should return quickly with all time-consuming operations being
	 * perfomed in one-or-more rendering passes.
	 * </p>
	 * 
	 * <p>
	 * Implementations will typically prepare the supplied view to display a
	 * blank placeholder, or a "loading" indicator. Note that a view may be
	 * prepared several times without receiving a render if it is part of an
	 * {@link AdapterView} that is recycling its views.
	 * </p>
	 * 
	 * @param view
	 *            a view will be displayed to the user presently
	 * @param param
	 *            defines the content that the view will display
	 * @param immediatePassHint
	 *            the pass to which rendering has already progressed
	 */
 
	protected abstract void prepare(View view, Param param, int immediatePassHint);
	
	/**
	 * Converts a Param into a Render for subsequent display in a view. The
	 * supplied pass parameter may be used to generate progressively more
	 * complete Renders. The first pass is zero. The number of passes is
	 * specified at construction time.
	 * 
	 * @param param
	 *            defines the rendering that needs to be produced
	 * @param pass
	 *            which rendering pass is being performed
	 * @return a rendering of the supplied parameters
	 */
	
	//TODO consider supplying the previous Render for multi-pass rendering
	protected abstract Render render(Param param, int pass);
 
	/**
	 * Applies previously rendered content to a view.
	 * 
	 * @param view
	 *            a view that needs to update with rendered content
	 * @param render
	 *            the content that is to be applied to the view
	 * @param pass
	 *            the rendering pass that generated the rendered content
	 */
 
	protected abstract void update(View view, Render render, int pass);
	
	/**
	 * Retrieves the last object that was associated with a view by the
	 * renderer. If no object has yet been associated with the view, null is
	 * returned.
	 * 
	 * @see ViewRenderer.setTag
	 * @param view
	 *            a rendered view
	 * @return the associated object or null
	 */
 
	protected Object getTag(View view) { return view.getTag(); }
 
	/**
	 * Associates an object with the view. The default implementation simply
	 * uses the {@link View.setTag(Object)} method. This may interfere with some
	 * layouts, so using the {@link View.setTag(int,Object)} method is
	 * preferable (API level 4 and above). A conservative implementation may be
	 * to use a {@link WeakHashMap}.
	 * 
	 * @param view
	 *            a rendered view
	 * @param tag
	 *            the object with which the view is to be tagged, may be null
	 */
	protected void setTag(View view, Object tag) { view.setTag(tag); }
	
	// private utility methods
	
	@SuppressWarnings("unchecked")
	private Task getTask(View view) {
		Object obj = getTag(view);
		return obj == null || (obj instanceof ViewRenderer.Task) ? (Task) obj : null;
	}
	
	private void setTask(View view, Task task) {
		setTag(view, task);
	}
	
	// inner classes
	
	private class Task implements Runnable, Callable<Void>, Comparable<Task> {
 
		// immutable fields for task
		private final long mOrder = mNextOrder++;
		private final Param mParam;
		
		// only set/mutated on UI thread
		// TODO look at strategy's for defraying cost of a hashset on each task
		// note that this set also incurs the cost of repeated iterator creation
		private final HashSet<View> mViews = new HashSet<View>();
		private Future<Void> mFuture;
		
		// only set on render thread
		private int mPass = 0; // from the UI thread, this is actually 'the next pass'
		private Render mRender = null;
		private boolean mFailed = false;
		
		public Task(Param param) {
			mParam = param;
		}
		
		void assignToView(View view) {
			// mPass is actually 'the next pass' at this point
			int pass = mPass - 1;
			// prepare the view for display while rendering
			prepare(view, mParam, pass);
			// update the view with whatever work we have already done
			if (pass >= 0) update(view, mRender, pass);
			//check if we have work left to do
			if (mPass < mPasses) {
				// add the view to our set
				mViews.add(view);
				// tag the view so that we can cancel the task later if we need to
				setTask(view, this);
				// all set, queue us up
				enqueue();
			}
		}
 
		void removeFromView(View view) {
			// mViews can't be null because keep tags and set in sync
			Task task = getTask(view);
			if (task == this) setTask(view, null);
			mViews.remove(view);
			if (mViews.isEmpty()) {
				cancel();
			}
		}
		
		void removeFromViews() {
			for (View view : mViews) {
				Task task = getTask(view);
				if (task == this) setTask(view, null);
			}
			mViews.clear();
			cancel();
		}
 
		// called to apply render to view
		@Override
		public void run() {
			if (mStopping) return;
			if (mFailed) {
				// render didn't complete (probably got cancelled)
				// remove from the view (but we may still get cached)
				removeFromViews();
			}
			// the views may be null/empty if we were removed from it while queued by the handler
			if (mViews != null && !mViews.isEmpty()) {
				try {
					for (View view : mViews) {
						// we are safe to update this view,
						// if another task had been started on the view
						// the view would have already have been removed from our set
						update(view, mRender, mPass - 1);
					}
				} finally {
					if (mPass < mPasses) {
						// reschedule again if we need to perform more passes
						enqueue();
					} else {
						// we're done, remove us from all our views
						// we lose the ability to avoid doing the same rendering for the same view (very rare)
						// but this is worth it, so that we can re-use the render from the cache (much more likely)
						removeFromViews();
					}
				}
			}
			// try putting us into the cache
			encache();
		}
		
		// called to produce render
		@Override
		public Void call() throws Exception {
			if (mStopping) return null;
			Process.setThreadPriority(getThreadPriority(mPass));
			try {
				Render render = render(mParam, mPass);
				if (render == null) {
					mFailed = true;
				} else {
					// record the render, we will apply to the view it cache it later 
					mRender = render;
					// increment the pass now - since later calls aren't guaranteed to occur
					mPass++;
				}
				mHandler.post(this);
				return null;
			} finally {
				Process.setThreadPriority(mInheritedThreadPriority);
			}
		}
		
		public int compareTo(Task that) {
			if (this == that) return 0;
			if (this.mPass != that.mPass) return this.mPass < that.mPass ? -1 : 1;
			if (this.mOrder != that.mOrder) return this.mOrder < that.mOrder ? -1 : 1;
			return 0;
		}
		
		private void enqueue() {
			mFailed = false;
			mFuture = mExecutor.submit((Callable<Void>) this);
		}
 
		private void encache() {
			if (mPass > 0 && mCache != null) {
				synchronized (mCache) {
					Task that = mCache.get(mParam);
					if (that == null || this.mPass > that.mPass) {
						mCache.put(mParam, this);
					}
				}
			}
		}
		
		// may only be called when all views have been removed
		private void cancel() {
			mFuture.cancel(mMayInterruptIfRunning);
		}
		
	}
 
	private class Cache extends LinkedHashMap<Param, Task> {
		
		// serialization boilerplate
		private static final long serialVersionUID = -5867267874566891476L;
		
		private final int mCapacity;
		
		public Cache(int capacity) {
			super(capacity, 0.75f, true);
			mCapacity = capacity;
		}
		
		@Override
		protected boolean removeEldestEntry(Map.Entry<Param, Task> eldest) {
			return size() >= mCapacity;
		}
		
	}
	
}
