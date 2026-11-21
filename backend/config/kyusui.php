<?php

return [
    /*
    | Upload rules: QRIS proof and owner QRIS image use the same allowlist
    | and limit. Keep these keys when adding other KYUSUI configuration.
    */
    'max_kilobytes' => (int) env('KYUSUI_UPLOAD_MAX_KILOBYTES', 5120),
    'image_mimes' => env('KYUSUI_IMAGE_MIMES', 'jpg,jpeg,png'),

    /*
    | Temporary service area defaults preserve the previously hard-coded
    | coordinates. Replace them only after Berkah Water's actual depot point
    | and service radius have been confirmed.
    */
    'service_area' => [
        'latitude' => (float) env('KYUSUI_SERVICE_AREA_LATITUDE', -0.8107389),
        'longitude' => (float) env('KYUSUI_SERVICE_AREA_LONGITUDE', 100.3709844),
        'radius_km' => (float) env('KYUSUI_SERVICE_AREA_RADIUS_KM', 10),
    ],
];
