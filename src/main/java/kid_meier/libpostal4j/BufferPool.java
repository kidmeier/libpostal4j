package kid_meier.libpostal4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.List;

class BufferPool implements SegmentAllocator {

	public static final long DEFAULT_SEGMENT_SIZE = 1024L * 1024L;
	public static final long DEFAULT_SEGMENT_ALIGNMENT = 1L;

	private final Arena arena;
	private final long segmentSize;
	private final long segmentAlignment;
	private final List<MemorySegment> segments;

	private int activeSegment;
	private long activeOffset;
	private SegmentAllocator segmentAllocator;

	public BufferPool(Arena arena) {
		this(arena, DEFAULT_SEGMENT_SIZE, DEFAULT_SEGMENT_ALIGNMENT);
	}

	public BufferPool(Arena arena, long segmentSize) {
		this(arena, segmentSize, DEFAULT_SEGMENT_ALIGNMENT);
	}

	public BufferPool(Arena arena, long segmentSize, long segmentAlignment) {
		this.arena = arena;
		this.segmentSize = segmentSize;
		this.segmentAlignment = segmentAlignment;
		this.segments = new ArrayList<>();
		this.activeSegment = -1;
		this.activeOffset = -1L;
		this.segmentAllocator = null;
	}

	public void reset() {
		reset(0L);
	}

	public void reset(long linearAddress) {
		if (linearAddress == 0L && segmentAllocator == null) {
			return;
		}
		int segmentIndex;
		try {
			segmentIndex = Math.toIntExact(linearAddress / segmentSize);
		} catch (ArithmeticException e) {
			throw new IndexOutOfBoundsException("Linear address out of range: " + linearAddress + " / " + segmentSize + " (" + e.getMessage() + ")");
		}
		if (segmentIndex > segments.size()) {
			throw new IndexOutOfBoundsException("Linear address out of range: " + linearAddress + " (>= " + (segmentSize * segments.size()) + ")");
		}
		reset(segmentIndex, linearAddress % segmentSize);
	}

	private void reset(int segmentIndex, long segmentOffset) {
		activeSegment = segmentIndex;
		activeOffset = segmentOffset;
		segmentAllocator = SegmentAllocator.slicingAllocator(segments.get(segmentIndex).asSlice(segmentOffset));
	}

	public long watermark() {
		return segmentSize*activeSegment + activeOffset;
	}

	private int allocSegment() {
		MemorySegment segment = arena.allocate(segmentSize, segmentAlignment);
		int segmentIndex = segments.size();
		segments.add(segment);
		return segmentIndex;
	}

	@Override
	public MemorySegment allocate(long byteSize, long byteAlignment) {
		if (segmentAllocator == null) {
			reset(allocSegment(), 0L);
		}
		MemorySegment segment;
		try {
			segment = segmentAllocator.allocate(byteSize, byteAlignment);
		} catch (IndexOutOfBoundsException e) {
			reset(allocSegment(), 0L);
			try {
				segment = segmentAllocator.allocate(byteSize, byteAlignment);
			} catch (IndexOutOfBoundsException ee) {
				throw new IllegalArgumentException("Request (" + byteSize + " bytes) exceeds segment size (" + segmentSize + ")");
			}
		}
		activeOffset += segment.byteSize();
		return segment;
	}

}
