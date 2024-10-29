package org.janelia.n5anndata;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import org.janelia.n5anndata.io.AnnDataField;
import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.n5anndata.io.AnnDataPath;
import org.janelia.n5anndata.io.AnnDataUtils;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class SpatialDataTest {
	// TODO: adjust this path to point to the location of the anndata dataset
	private static final String containerPath = System.getenv("HOME");
	private static final String datasetPath = Paths.get(containerPath, "Downloads", "ISS_demo_tiny.sdata", "tables", "table").toString();

	public static void main(final String[] args) {
		try (final N5Reader anndataN5 = new N5ZarrReader(datasetPath)) {
			// List
			System.out.println("All groups present:");
			Arrays.stream(anndataN5.deepList("")).map(s -> " - " + s).forEach(System.out::println);


			// Get basic size information about the AnnData
			System.out.println("\nNumber of observations: " + AnnDataUtils.getNObs(anndataN5));
			System.out.println("Number of variables: " + AnnDataUtils.getNVar(anndataN5));

			// Read central matrix X
			final Img<DoubleType> X = AnnDataUtils.readNumericalArray(anndataN5, AnnDataField.X.toString());

			// Read obs index
			final List<String> obsIndex = AnnDataUtils.readDataFrameIndex(anndataN5, AnnDataField.OBS.toString());
			System.out.println("\nObs index:");
			obsIndex.stream().limit(5).map(s -> " - " + s).forEach(System.out::println);
			System.out.println(" - ...");

			// Read region annotation (which is a categorical variable)
			final List<String> regions = AnnDataUtils.readStringArray(anndataN5, "obs/region");
			System.out.println("\nRegions:");
			regions.stream().limit(5).map(s -> " - " + s).forEach(System.out::println);
			System.out.println(" - ...");

			// Read instance id (and read the field type to see if it's numerical or string data)
			final AnnDataFieldType fieldType = AnnDataUtils.getFieldType(anndataN5, "obs/instance_id");
			System.out.println("\nInstance id (" + fieldType + "):");
			if (fieldType.equals(AnnDataFieldType.STRING_ARRAY) || fieldType.equals(AnnDataFieldType.CATEGORICAL_ARRAY)) {
				AnnDataUtils.readStringArray(anndataN5, "obs/instance_id").stream().limit(5).map(s -> " - " + s).forEach(System.out::println);
			} else {
				final Interval interval = new FinalInterval(5);
				final Img<LongType> instanceId = AnnDataUtils.readNumericalArray(anndataN5, "obs/instance_id");
				Views.interval(instanceId, interval).cursor().forEachRemaining(d -> System.out.println(" - " + d.get()));
			}

			// Read var index
			// Note: this lives in a group named "Names" rather than the default "_index", which is discovered automatically
			final List<String> varIndex = AnnDataUtils.readDataFrameIndex(anndataN5, AnnDataField.VAR.toString());
			System.out.println("\nVar index:");
			varIndex.stream().limit(5).map(s -> " - " + s).forEach(System.out::println);
			System.out.println(" - ...");

			// Read metadata for remaining fields
			final AnnDataPath attrsPath = new AnnDataPath(AnnDataField.UNS, "spatialdata_attrs");
			final String[] unsFields = anndataN5.list(attrsPath.toString());
			final AnnDataFieldType fieldTypeParent = AnnDataUtils.getFieldType(anndataN5, AnnDataField.UNS.toString());
			System.out.println("\n" + unsFields.length + " fields in '" + attrsPath + "' (" + fieldTypeParent + "):");
			for (final String field : unsFields) {
				final AnnDataPath path = new AnnDataPath(AnnDataField.UNS, "spatialdata_attrs", field);
				final AnnDataFieldType type = AnnDataUtils.getFieldType(anndataN5, path);
				final DatasetAttributes attributes = anndataN5.getDatasetAttributes(path.toString());
				System.out.println(" - " + field + " (" + type + "): " + Arrays.toString(attributes.getDimensions()));
			}
		}
	}
}
