package org.traccar.iksupdate;

import jakarta.inject.Inject;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.reports.common.ReportUtils;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.util.*;

public class MovingPosition {
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public MovingPosition(ReportUtils reportUtils, Storage storage) {
        this.reportUtils = reportUtils;
        this.storage = storage;
    }


    public static List<Position> getPositionsMotion(
            Storage storage, long deviceId) throws StorageException {
        var singlePosition = storage.getObject(Position.class,
                new Request(new Columns.All(),
                        new Condition.Equals("deviceId", deviceId), new Order("fixTime", true, 1)));

        if (singlePosition == null) {
            return new ArrayList<>();
        }

        Map<String, Object> attributes = singlePosition.getAttributes();
        Object motionValue = attributes.get("motion");

        if (motionValue != null && (Boolean) motionValue) {
            Calendar fromCalendar = Calendar.getInstance();
            fromCalendar.setTime(singlePosition.getFixTime());
            fromCalendar.add(Calendar.HOUR_OF_DAY, -24);

            var positions = storage.getObjects(Position.class, new Request(
                    new Columns.All(),
                    new Condition.And(
                            new Condition.Equals("deviceId", deviceId),
                            new Condition.Between("fixTime", "from", fromCalendar.getTime(), "to", singlePosition.getFixTime())),
                    new Order("fixTime", true, 0)));

            List<Position> filteredPositions = new ArrayList<>();

            for (Position position : positions) {
                Map<String, Object> positionAttributes = position.getAttributes();
                Object positionMotionValue = positionAttributes.get("motion");

                if (positionMotionValue != null && (Boolean) positionMotionValue) {
                    filteredPositions.add(position);
                } else {
                    break;
                }
            }

            return filteredPositions;
        }

        return new ArrayList<>();
    }

    public static List<Position> getPositionsMotionSlow(
            Storage storage, long deviceId) throws StorageException {
        var startTrip = storage.getObject(Event.class, new Request(new Columns.All(),
                new Condition.And(new Condition.Equals("deviceId", deviceId),new Condition.Equals("type", Event.TYPE_DEVICE_MOVING)),
                new Order("eventtime", true, 1)));

        if (startTrip == null) {
            return new ArrayList<>();
        }
        var endTrip = storage.getObject(Event.class, new Request(new Columns.All(),
                new Condition.And(new Condition.Equals("deviceId", deviceId),new Condition.Equals("type", Event.TYPE_DEVICE_STOPPED)),
                new Order("eventtime", true, 1)));

        if (endTrip != null) {
            if (startTrip.getEventTime().compareTo(endTrip.getEventTime()) < 0) {
                return new ArrayList<>();
            }
        }
        var lastestPosition
                = storage.getObject(Position.class, new Request(new Columns.All(),
                new Condition.Equals("deviceId", deviceId),
                new Order("fixTime", true, 1)));

        return storage.getObjects(Position.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("deviceId", deviceId),
                        new Condition.Between("fixTime", "from", startTrip.getEventTime(), "to", lastestPosition.getFixTime())),
                new Order("fixTime", true, 0)));
    }

    public Collection<Position> getObjects(long userId, Collection<Long> deviceIds, Collection<Long> groupIds) throws StorageException {

        ArrayList<Position> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(MovingPosition.getPositionsMotionSlow(storage, device.getId()));
        }
        return result;
    }

}

