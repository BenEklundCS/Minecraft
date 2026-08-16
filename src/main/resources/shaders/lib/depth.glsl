// Undo the perspective divide's 1/z distribution: turn a stored [0,1] depth back into a distance
// along the view axis, in blocks. Both planes must match Camera.java:12-13 or every distance this
// returns is silently wrong.
uniform float uCameraNear;
uniform float uCameraFar;

float linearDepth(float storedDepth) {
    // ndc runs -1..1; the stored value runs 0..1.
    float ndc = storedDepth * 2.0 - 1.0;
    return (2.0 * uCameraNear * uCameraFar)
    / (uCameraFar + uCameraNear - ndc * (uCameraFar - uCameraNear));
}