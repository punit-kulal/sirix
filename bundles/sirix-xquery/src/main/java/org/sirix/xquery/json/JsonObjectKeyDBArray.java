package org.sirix.xquery.json;

import java.util.ArrayList;
import java.util.List;
import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.Int64;
import org.brackit.xquery.atomic.IntNumeric;
import org.brackit.xquery.xdm.AbstractItem;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.json.Array;
import org.brackit.xquery.xdm.json.TemporalJsonItem;
import org.brackit.xquery.xdm.type.ArrayType;
import org.brackit.xquery.xdm.type.ItemType;
import org.sirix.api.NodeReadOnlyTrx;
import org.sirix.api.json.JsonNodeReadOnlyTrx;
import org.sirix.api.json.JsonNodeTrx;
import org.sirix.api.json.JsonResourceManager;
import org.sirix.axis.AbstractTemporalAxis;
import org.sirix.axis.ChildAxis;
import org.sirix.axis.IncludeSelf;
import org.sirix.axis.temporal.AllTimeAxis;
import org.sirix.axis.temporal.FirstAxis;
import org.sirix.axis.temporal.FutureAxis;
import org.sirix.axis.temporal.LastAxis;
import org.sirix.axis.temporal.NextAxis;
import org.sirix.axis.temporal.PastAxis;
import org.sirix.axis.temporal.PreviousAxis;
import org.sirix.node.NodeKind;
import org.sirix.utils.LogWrapper;
import org.sirix.xquery.StructuredDBItem;
import org.sirix.xquery.stream.json.TemporalSirixJsonObjectKeyArrayStream;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;

public final class JsonObjectKeyDBArray extends AbstractItem
    implements TemporalJsonItem<JsonObjectKeyDBArray>, Array, StructuredDBItem<JsonNodeReadOnlyTrx>, JsonDBItem {

  /** {@link LogWrapper} reference. */
  private static final LogWrapper LOGWRAPPER = new LogWrapper(LoggerFactory.getLogger(JsonObjectKeyDBArray.class));

  /** Sirix read-only transaction. */
  private final JsonNodeReadOnlyTrx rtx;

  /** Sirix node key. */
  private final long nodeKey;

  /** Kind of node. */
  private final org.sirix.node.NodeKind kind;

  /** Collection this node is part of. */
  private final JsonDBCollection collection;

  /** Determines if write-transaction is present. */
  private final boolean isWtx;

  private JsonItemFactory jsonUtil;

  /**
   * Constructor.
   *
   * @param rtx {@link JsonNodeReadOnlyTrx} for providing reading access to the underlying node
   * @param collection {@link JsonDBCollection} reference
   */
  public JsonObjectKeyDBArray(final JsonNodeReadOnlyTrx rtx, final JsonDBCollection collection) {
    this.collection = Preconditions.checkNotNull(collection);
    this.rtx = Preconditions.checkNotNull(rtx);
    isWtx = this.rtx instanceof JsonNodeTrx;
    nodeKey = this.rtx.getNodeKey();
    assert this.rtx.isObject();
    kind = NodeKind.ARRAY;
    jsonUtil = new JsonItemFactory();
  }

  @Override
  public long getNodeKey() {
    moveRtx();

    return rtx.getNodeKey();
  }

  private final void moveRtx() {
    rtx.moveTo(nodeKey);
  }

  @Override
  public JsonDBCollection getCollection() {
    return collection;
  }

  @Override
  public JsonNodeReadOnlyTrx getTrx() {
    return rtx;
  }

  @Override
  public JsonObjectKeyDBArray getNext() {
    moveRtx();

    final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis = new NextAxis<>(rtx.getResourceManager(), rtx);
    return moveTemporalAxis(axis);
  }

  private JsonObjectKeyDBArray moveTemporalAxis(final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis) {
    if (axis.hasNext()) {
      final var rtx = axis.next();
      return new JsonObjectKeyDBArray(rtx, collection);
    }

    return null;
  }

  @Override
  public JsonObjectKeyDBArray getPrevious() {
    moveRtx();
    final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis =
        new PreviousAxis<>(rtx.getResourceManager(), rtx);
    return moveTemporalAxis(axis);
  }

  @Override
  public JsonObjectKeyDBArray getFirst() {
    moveRtx();
    final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis =
        new FirstAxis<>(rtx.getResourceManager(), rtx);
    return moveTemporalAxis(axis);
  }

  @Override
  public JsonObjectKeyDBArray getLast() {
    moveRtx();
    final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis = new LastAxis<>(rtx.getResourceManager(), rtx);
    return moveTemporalAxis(axis);
  }

  @Override
  public Stream<JsonObjectKeyDBArray> getEarlier(final boolean includeSelf) {
    moveRtx();
    final IncludeSelf include = includeSelf
        ? IncludeSelf.YES
        : IncludeSelf.NO;
    return new TemporalSirixJsonObjectKeyArrayStream(new PastAxis<>(rtx.getResourceManager(), rtx, include),
        collection);
  }

  @Override
  public Stream<JsonObjectKeyDBArray> getFuture(final boolean includeSelf) {
    moveRtx();
    final IncludeSelf include = includeSelf
        ? IncludeSelf.YES
        : IncludeSelf.NO;
    return new TemporalSirixJsonObjectKeyArrayStream(new FutureAxis<>(rtx.getResourceManager(), rtx, include),
        collection);
  }

  @Override
  public Stream<JsonObjectKeyDBArray> getAllTimes() {
    moveRtx();
    return new TemporalSirixJsonObjectKeyArrayStream(new AllTimeAxis<>(rtx.getResourceManager(), rtx), collection);
  }

  @Override
  public boolean isNextOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (this == other)
      return false;

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    return otherNode.getTrx().getRevisionNumber() - 1 == this.getTrx().getRevisionNumber();
  }

  @Override
  public boolean isPreviousOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (this == other)
      return false;

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    return otherNode.getTrx().getRevisionNumber() + 1 == this.getTrx().getRevisionNumber();
  }

  @Override
  public boolean isFutureOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (this == other)
      return false;

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    return otherNode.getTrx().getRevisionNumber() > this.getTrx().getRevisionNumber();
  }

  @Override
  public boolean isFutureOrSelfOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (this == other)
      return true;

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    return otherNode.getTrx().getRevisionNumber() - 1 >= this.getTrx().getRevisionNumber();
  }

  @Override
  public boolean isEarlierOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (this == other)
      return false;

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    return otherNode.getTrx().getRevisionNumber() < this.getTrx().getRevisionNumber();
  }

  @Override
  public boolean isEarlierOrSelfOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (this == other)
      return true;

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    return otherNode.getTrx().getRevisionNumber() <= this.getTrx().getRevisionNumber();
  }

  @Override
  public boolean isLastOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    final NodeReadOnlyTrx otherTrx = otherNode.getTrx();

    return otherTrx.getResourceManager().getMostRecentRevisionNumber() == otherTrx.getRevisionNumber();
  }

  @Override
  public boolean isFirstOf(final JsonObjectKeyDBArray other) {
    moveRtx();

    if (!(other instanceof JsonObjectKeyDBArray))
      return false;

    final JsonObjectKeyDBArray otherNode = other;
    final NodeReadOnlyTrx otherTrx = otherNode.getTrx();

    // Revision 0 is just the bootstrap revision and not accessed over here.
    return otherTrx.getRevisionNumber() == 1;
  }

  @Override
  public ItemType itemType() {
    return ArrayType.ARRAY;
  }

  @Override
  public Atomic atomize() {
    throw new QueryException(ErrorCode.ERR_ITEM_HAS_NO_TYPED_VALUE, "The atomized value of array items is undefined");
  }

  @Override
  public boolean booleanValue() {
    throw new QueryException(ErrorCode.ERR_ITEM_HAS_NO_TYPED_VALUE, "The boolean value of array items is undefined");
  }

  @Override
  public List<Sequence> values() {
    moveRtx();

    final List<Sequence> values = new ArrayList<Sequence>();

    for (int i = 0, length = len(); i < length; i++)
      values.add(at(i));

    return values;
  }

  private Sequence getSequenceAtIndex(final JsonNodeReadOnlyTrx rtx, final int index) {
    moveRtx();

    final var axis = new ChildAxis(rtx);

    for (int i = 0; i < index && axis.hasNext(); i++)
      axis.next();

    if (axis.hasNext()) {
      axis.next();

      return jsonUtil.getSequence(rtx, collection);
    }

    return null;
  }

  @Override
  public Sequence at(final IntNumeric numericIndex) {
    return getSequenceAtIndex(rtx, numericIndex.intValue());
  }

  @Override
  public Sequence at(final int index) {
    return getSequenceAtIndex(rtx, index);
  }

  @Override
  public IntNumeric length() {
    moveRtx();
    return new Int64(rtx.getChildCount());
  }

  @Override
  public int len() {
    moveRtx();
    return (int) rtx.getChildCount();
  }

  @Override
  public Array range(IntNumeric from, IntNumeric to) {
    moveRtx();

    return new JsonDBArraySlice(rtx, collection, from.intValue(), to.intValue());
  }

  @Override
  public JsonResourceManager getResourceManager() {
    return rtx.getResourceManager();
  }
}
