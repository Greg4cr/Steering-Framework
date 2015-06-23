/*	SteeringDataException
	Custom exception for data import/formatting issues.

	Gregory Gay (greg@greggay.com)
	Last Updated: 05/13/2014
		- Initial file creation

 */

package steering;

public class SteeringDataException extends Exception {

	private static final long serialVersionUID = 1L;

	public SteeringDataException() {
		// TODO Auto-generated constructor stub
	}

	public SteeringDataException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public SteeringDataException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}

	public SteeringDataException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	public SteeringDataException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
		// TODO Auto-generated constructor stub
	}

}
