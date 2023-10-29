/*
*
* Description: this is a simple conical noise cone generated for OpenScad
*               you can compile it and export it to an STL file so that it can be 3d printed
*
*
*/
$fn=200; //noise cone resolution
R=##aftradius##;
L=##length##;
thickness=##thickness##;
Rshoulder=##aftshoulderradius##;
Hshoulder=##aftshoulderlength##;
TShoulder=##aftshoulderthickness##;
/*10;
L=100;
thickness=1.5;
Rshoulder=8.5;
Hshoulder=10;*/

//noise cone itself
difference() {
cylinder(r1 =R, r2 = 0,  h =L, center = true);
translate([0,0,-thickness])cylinder(r1 =R, r2 = 0,  h =L, center = true);
}
//shoulder
translate([0,0,-L/2])difference() {
    cylinder(r=Rshoulder, h=Hshoulder, center = true);
    cylinder(r=Rshoulder-TShoulder, h=Hshoulder, center =true);
}
