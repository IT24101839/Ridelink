package com.ridelink.ride.dto;

import com.ridelink.ride.model.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateRideRequest(@NotNull @Valid Location pickup,
                                @NotNull @Valid Location destination) {
}
