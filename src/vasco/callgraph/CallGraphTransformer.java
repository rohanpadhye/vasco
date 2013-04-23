/**
 * Copyright (C) 2013 Rohan Padhye
 * 
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
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
