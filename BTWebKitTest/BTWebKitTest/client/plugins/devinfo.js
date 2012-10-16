var DeviceInfoPlugin = function () {
    log('Dev info:', localStorage['test'], localStorage, localStorage.test);
    localStorage['test'] = 'zzz';
}

registerPlugin(new DeviceInfoPlugin());