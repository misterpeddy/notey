### Testing

To start adb's daemon on device

```
adb devices
adb -s $DEVICE_ID tcpip 5555
```

To connect to a (remote) device
```
adb connect $DEVICE_IP
```
