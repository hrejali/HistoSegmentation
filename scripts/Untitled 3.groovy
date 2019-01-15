import qupath.lib.roi.RectangleROI
import qupath.lib.objects.PathAnnotationObject

import qupath.lib.images.servers.ImageServer
import qupath.lib.objects.PathObject
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.PathROIToolsAwt
import qupath.lib.scripting.QPEx

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage

// Get center pixel
def viewer = getCurrentViewer()
int cx = viewer.getCenterPixelX()
int cy = viewer.getCenterPixelY()

// Get the main QuPath data structures
def imageData = QPEx.getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()

int width = server.getWidth()
int height = server.getHeight()

// Create & add annotation
def roi = new RectangleROI(cx-width/2,cy-height/2,width, height)
def annotation = new PathAnnotationObject(roi)
addObject(annotation)



setImageType('OTHER');
selectAnnotations();
runPlugin('qupath.lib.algorithms.TilerPlugin', '{"tileSizePx": 5.0,  "trimToROI": true,  "makeAnnotations": false,  "removeParentAnnotation": false}');
selectDetections();
runPlugin('qupath.lib.algorithms.IntensityFeaturesPlugin', '{"downsample": 1.0,  "region": "ROI",  "tileSizePixels": 200.0,  "colorRed": true,  "colorGreen": true,  "colorBlue": true,  "colorHue": true,  "colorSaturation": true,  "colorBrightness": true,  "doMean": true,  "doStdDev": true,  "doMinMax": true,  "doMedian": true,  "doHaralick": true,  "haralickDistance": 1,  "haralickBins": 32}');
selectAnnotations();
runPlugin('qupath.lib.plugins.objects.SmoothFeaturesPlugin', '{"fwhmPixels": 50.0,  "smoothWithinClasses": false,  "useLegacyNames": false}');
runClassifier('C:\\Users\\Hossein\\Documents\\MASc\\Projects\\Segmentation\\classifiers\\TissueSegmentation.qpclassifier');
selectAnnotations();
runPlugin('qupath.lib.analysis.objects.TileClassificationsToAnnotationsPlugin', '{"pathClass": "All classes",  "deleteTiles": false,  "clearAnnotations": true,  "splitAnnotations": false}');
//............................................................................... Export Each Annotation as a Binary Image ............................................................................................................
// Get the main QuPath data structures
def imageData = QPEx.getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()

// Request all objects from the hierarchy & filter only the annotations
def annotations = hierarchy.getFlattenedObjectList(null).findAll {it.isAnnotation()}

// Define downsample value for export resolution & output directory, creating directory if necessary
def downsample = 1
def pathOutput = QPEx.buildFilePath(QPEx.PROJECT_BASE_DIR, 'masks')
QPEx.mkdirs(pathOutput)

// Define image export type; valid values are JPG, PNG or null (if no image region should be exported with the mask)
// Note: masks will always be exported as PNG
def imageExportType = 'JPG'

// Export each annotation
annotations.each {
    saveImageAndMask(pathOutput, server, it, downsample, imageExportType)
}
print 'Done!'

/**
 * Save extracted image region & mask corresponding to an object ROI.
 *
 * @param pathOutput Directory in which to store the output
 * @param server ImageServer for the relevant image
 * @param pathObject The object to export
 * @param downsample Downsample value for the export of both image region & mask
 * @param imageExportType Type of image (original pixels, not mask!) to export ('JPG', 'PNG' or null)
 * @return
 */
def saveImageAndMask(String pathOutput, ImageServer server, PathObject pathObject, double downsample, String imageExportType) {
    // Extract ROI & classification name
    def roi = pathObject.getROI()
    def pathClass = pathObject.getPathClass()
    def classificationName = pathClass == null ? 'None' : pathClass.toString()
    if (roi == null) {
        print 'Warning! No ROI for object ' + pathObject + ' - cannot export corresponding region & mask'
        return
    }

    // Create a region from the ROI
    def region = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight())

    // Create a name
    String name = String.format('%s_%s',
            server.getShortServerName(),
            classificationName,
            //region.getDownsample(),
            //region.getX(),
            //region.getY(),
            //region.getWidth(),
            //region.getHeight()
    )

    // Request the BufferedImage
    def img = server.readBufferedImage(region)

    // Create a mask using Java2D functionality
    // (This involves applying a transform to a graphics object, so that none needs to be applied to the ROI coordinates)
    def shape = PathROIToolsAwt.getShape(roi)
    def imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY)
    def g2d = imgMask.createGraphics()
    g2d.setColor(Color.WHITE)
    g2d.scale(1.0/downsample, 1.0/downsample)
    g2d.translate(-region.getX(), -region.getY())
    g2d.fill(shape)
    g2d.dispose()

    // Create filename & export
    if (imageExportType != null) {
        def fileImage = new File(pathOutput, name + '.' + imageExportType.toLowerCase())
        //ImageIO.write(img, imageExportType, fileImage)
    }
    // Export the mask
    def fileMask = new File(pathOutput, name + '-mask.png')
    ImageIO.write(imgMask, 'PNG', fileMask)

}
