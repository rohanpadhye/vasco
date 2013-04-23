package vasco.callgraph;

import java.util.Map;

import soot.SceneTransformer;

/**
 * A Soot {@link SceneTransformer} for performing {@link PointsToAnalysis}.
 * 
 * @author Rohan Padhye
 */
public class CallGraphTransformer extends SceneTransformer {
	
	private PointsToAnalysis pointsToAnalysis;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalTransform(String arg0, @SuppressWarnings("rawtypes") Map arg1) {
		pointsToAnalysis = new PointsToAnalysis();
		pointsToAnalysis.doAnalysis();
	}
	
	/**
	 * Returns a reference to the {@link PointsToAnalysis} object. 
	 * @return a reference to the {@link PointsToAnalysis} object
	 */
	public PointsToAnalysis getPointsToAnalysis() {
		return pointsToAnalysis;
	}

	
	
}
