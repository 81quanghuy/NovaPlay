package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.repository.IWatchProgressRepository;
import vn.iotstar.userservice.service.IWatchHistoryService;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchHistoryService implements IWatchHistoryService {
    private final IWatchProgressRepository watchHistoryRepository;


}
