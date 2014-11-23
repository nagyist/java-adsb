package org.opensky.libadsb;

import java.io.Serializable;

/**
 *  This file is part of org.opensky.libadsb.
 *
 *  org.opensky.libadsb is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  org.opensky.libadsb is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with org.opensky.libadsb.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Decoder for ADS-B airborne position messages
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class AirbornePositionMsg extends ExtendedSquitter implements Serializable {

	private static final long serialVersionUID = 8593107448574597504L;
	private boolean horizontal_position_available;
	private boolean altitude_available;
	private byte surveillance_status;
	private boolean single_antenna_flag;
	private short altitude_encoded;
	private boolean time_flag;
	private boolean cpr_format;
	private int cpr_encoded_lat;
	private int cpr_encoded_lon;
	private byte nic_suppl;

	/**
	 * @param raw_message raw ADS-B airborne position message as hex string
	 * @throws Exception if message has wrong format
	 */
	public AirbornePositionMsg(String raw_message) throws Exception {
		super(raw_message);

		if (!(getFormatTypeCode() == 0 ||
				(getFormatTypeCode() >= 9 && getFormatTypeCode() <= 18) ||
				(getFormatTypeCode() >= 20 && getFormatTypeCode() <= 22))) 
			throw new Exception("This is not a position message! Wrong format type code ("+getFormatTypeCode()+").");

		byte[] msg = getMessage();

		horizontal_position_available = getFormatTypeCode() != 0;

		surveillance_status = (byte) ((msg[0]>>>1)&0x3);
		single_antenna_flag = (msg[0]&0x1) == 1;

		altitude_encoded = (short) (((msg[1]<<4)|((msg[2]>>>4)&0xF))&0xFFF);
		altitude_available = altitude_encoded != 0;

		time_flag = ((msg[2]>>>3)&0x1) == 1;
		cpr_format = ((msg[2]>>>2)&0x1) == 1;
		cpr_encoded_lat = (((msg[2]&0x3)<<15) | ((msg[3]&0xFF)<<7) | ((msg[4]>>>1)&0x7F)) & 0x1FFFF;
		cpr_encoded_lon = (((msg[4]&0x1)<<16) | ((msg[5]&0xFF)<<8) | (msg[6]&0xFF)) & 0x1FFFF;
	}

	/**
	 * @return NIC supplement that was set before
	 */
	public byte getNICSupplement() {
		return nic_suppl;
	}


	/**
	 * @param nic_suppl Navigation Integrity Category (NIC) supplement from operational status message.
	 *        Otherwise worst case is assumed for containment radius limit and NIC.
	 */
	public void setNICSupplement(byte nic_suppl) {
		this.nic_suppl = nic_suppl;
	}

	/**
	 * @return horizontal containment radius limit in meters. A return value of 0 means "unkown".
	 *         If NIC supplement is set before, the return value is exactly according to DO-260B.
	 *         Otherwise it can be a little worse than it actually is.
	 */
	public double getHorizontalContainmentRadiusLimit() {
		switch (getFormatTypeCode()) {
		case 0: return 0;
		case 9: return 7.5;
		case 10: return 25;
		case 11:
			if ((nic_suppl&0x6) == 0x6) return 75;
			else return 185.2;
		case 12: return 370.4;
		case 13:
			if ((nic_suppl&0x6) == 0x2) return 555.6;
			else if ((nic_suppl&0x6) == 0) return 926;
			else return 1111.2;
		case 14: return 1852;
		case 15: return 3704;
		case 16:
			if ((nic_suppl&0x6) == 0x6) return 7408;
			else return 14816;
		case 17: return 37040;
		case 18: return 0;
		case 20: return 7.5;
		case 21: return 25;
		case 22: return 0;
		default: return 0;
		}
	}

	/**
	 * @return Navigation integrity category. A NIC of 0 means "unkown".
	 *         If NIC supplement is set before, the return value is exactly according to DO-260B.
	 *         Otherwise it might be a little worse than it actually is.
	 */
	public byte getNavigationIntegrityCategory() {
		switch (getFormatTypeCode()) {
		case 0: return 0;
		case 9: return 11;
		case 10: return 10;
		case 11:
			if ((nic_suppl&0x6) == 0x6) return 9;
			else return 8;
		case 12: return 7;
		case 13: return 6;
		case 14: return 5;
		case 15: return 4;
		case 16:
			if ((nic_suppl&0x6) == 0x6) return 3;
			else return 2;
		case 17: return 1;
		case 18: return 0;
		case 20: return 11;
		case 21: return 10;
		case 22: return 0;
		default: return 0;
		}
	}

	/**
	 * @return whether horizontal position information is available
	 */
	public boolean hasPosition() {
		return horizontal_position_available;
	}

	/**
	 * @return whether altitude information is available
	 */
	public boolean hasAltitude() {
		return altitude_available;
	}

	/**
	 * @see getSurveillanceStatusDescription()
	 * @return the surveillance status
	 */
	public byte getSurveillanceStatus() {
		return surveillance_status;
	}

	/**
	 * This is a function of the surveillance status field in the position
	 * message.
	 * 
	 * @return surveillance status description as defines in DO-260B
	 */
	public String getSurveillanceStatusDescription() {
		String[] desc = {
				"No condition information",
				"Permanent alert (emergency condition)",
				"Temporary alert (change in Mode A identity code oter than emergency condition)",
				"SPI condition"
		};

		return desc[surveillance_status];
	}

	/**
	 * @return true, if transmitting system uses only one antenna. Otherwise it uses two.
	 */
	public boolean hasSingleAntenna() {
		return single_antenna_flag;
	}

	/**
	 * @return flag which will indicate whether or not the Time of Applicability of the message
	 *         is synchronized with UTC time. False will denote that the time is not synchronized
	 *         to UTC. True will denote that Time of Applicability is synchronized to UTC time.
	 */
	public boolean isTime_flag() {
		return time_flag;
	}

	/**
	 * @return the CPR encoded binary latitude
	 */
	public int getCPREncodedLatitude() {
		return cpr_encoded_lat;
	}

	/**
	 * @return the CPR encoded binary longitude
	 */
	public int getCPREncodedLongitude() {
		return cpr_encoded_lon;
	}

	/**
	 * @return whether message is odd format. Returns false if message is even format. This is
	 *         needed for position decoding as the CPR algorithm uses both formats.
	 */
	public boolean isOddFormat() {
		return cpr_format;
	}

	/**
	 * @return true, if barometric altitude. False if GNSS is used to determine altitude
	 */
	public boolean isBarometricAltitude() {
		return this.getFormatTypeCode() < 20;
	}

	/**
	 * @param Rlat Even or odd Rlat value (CPR internal)
	 * @return the number of even longitude zones at a latitude
	 */
	private double NL(double Rlat) {
		if (Rlat == 0) return 59;
		else if (Math.abs(Rlat) == 87) return 2;
		else if (Math.abs(Rlat) > 87) return 1;

		double tmp = 1-(1-Math.cos(Math.PI/(2.0*15.0)))/Math.pow(Math.cos(Math.PI/180.0*Math.abs(Rlat)), 2);
		return Math.floor(2*Math.PI/Math.acos(tmp));
	}
	
	/**
	 * Modulo operator in java has stupid behavior
	 */
	private static double mod(double a, double b) {
		return ((a%b)+b)%b;
	}

	/**
	 * This method can only be used if another position report with a different format (even/odd) is available
	 * and set with msg.setOtherFormatMsg(other).
	 * @param other airborne position message of the other format (even/odd). Note that the time between
	 *        both messages should be not longer than 10 seconds! 
	 * @return globally unambiguously decoded position tuple (latitude, longitude). The positional
	 *         accuracy maintained by the Airborne CPR encoding will be approximately 5.1 meters.
	 *         A message of the other format is needed for global decoding.
	 * @throws Exception if no position information is available in one of the messages
	 * @throws org.opensky.libadsb.exceptions.PositionsIncompatibleError if position messages straddle latitude transition
	 */
	public double[] getGlobalPosition(AirbornePositionMsg other) throws Exception {
		if (!tools.areEqual(other.getIcao24(), getIcao24()))
				throw new Exception(
						String.format("Transmitter of other message (%s) not equal to this (%s).",
						tools.toHexString(other.getIcao24()), tools.toHexString(this.getIcao24())));
		
		if (other.isOddFormat() == this.isOddFormat())
			throw new Exception("Expected "+(isOddFormat()?"even":"odd")+" message format.");

		if (!horizontal_position_available)
			throw new Exception("No position information available!");
		if (!other.hasPosition())
			throw new Exception("Other message has no position information.");

		AirbornePositionMsg even = isOddFormat()?other:this;
		AirbornePositionMsg odd = isOddFormat()?this:other;

		// Helper for latitude single(Number of zones NZ is set to 15)
		double Dlat0 = 360.0/60.0;
		double Dlat1 = 360.0/59.0;

		// latitude index
		double j = Math.floor((59.0*even.getCPREncodedLatitude()-60.0*odd.getCPREncodedLatitude())/((double)(1<<17))+0.5);

		// global latitudes
		double Rlat0 = Dlat0 * (mod(j,60)+even.getCPREncodedLatitude()/((double)(1<<17)));
		double Rlat1 = Dlat1 * (mod(j,59)+odd.getCPREncodedLatitude()/((double)(1<<17)));

		// Southern hemisphere?
		if (Rlat0 >= 270 && Rlat0 <= 360) Rlat0 -= 360;
		if (Rlat1 >= 270 && Rlat1 <= 360) Rlat1 -= 360;

		// Northern hemisphere?
		if (Rlat0 <= -270 && Rlat0 >= -360) Rlat0 += 360;
		if (Rlat1 <= -270 && Rlat1 >= -360) Rlat1 += 360;

		// ensure that the number of even longitude zones are equal
		if (NL(Rlat0) != NL(Rlat1))
			throw new org.opensky.libadsb.exceptions.PositionsIncompatibleError(
				"The two given position straddle a transition latitude "+
				"and cannot be decoded. Wait for positions where they are equal.");

		// Helper for longitude
		double Dlon0 = 360.0/Math.max(1.0, NL(Rlat0));
		double Dlon1 = 360.0/Math.max(1.0, NL(Rlat1)-1);

		// longitude index
		double NL_helper = NL(isOddFormat()?Rlat1:Rlat0); // assuming that this is the newer message
		double m = Math.floor((even.getCPREncodedLongitude()*(NL_helper-1)-odd.getCPREncodedLongitude()*NL_helper)/((double)(1<<17))+0.5);

		// global longitude
		double Rlon0 = Dlon0 * (mod(m,Math.max(1.0, NL(Rlat0))) + even.getCPREncodedLongitude()/((double)(1<<17)));
		double Rlon1 = Dlon1 * (mod(m,Math.max(1.0, NL(Rlat1)-1)) + odd.getCPREncodedLongitude()/((double)(1<<17)));

		// correct longitude
		if (Rlon0 < -180 && Rlon0 > -360) Rlon0 += 360;
		if (Rlon1 < -180 && Rlon1 > -360) Rlon1 += 360;
		if (Rlon0 > 180 && Rlon0 < 360) Rlon0 -= 360;
		if (Rlon1 > 180 && Rlon1 < 360) Rlon1 -= 360;
		
		return new double[] {isOddFormat()?Rlat1:Rlat0, isOddFormat()?Rlon1:Rlon0};
	}
	
	/**
	 * This method uses a locally unambiguous decoding for airborne position messages. It
	 * uses a reference position known to be within 180NM (= 333.36km) of the true target
	 * airborne position. the reference point may be a previously tracked position that has
	 * been confirmed by global decoding (see getGlobalPosition()).
	 * @param ref_lat latitude of reference position
	 *        ref_lon longitude of reference position
	 * @return decoded position as tuple (latitude, longitude). The positional
	 *         accuracy maintained by the Airborne CPR encoding will be approximately 5.1 meters.
	 * @throws Exception if no position information is available
	 */
	public double[] getLocalPosition(double ref_lat, double ref_lon) throws Exception {
		if (!horizontal_position_available)
			throw new Exception("No position information available!");
		
		// latitude zone size
		double Dlat = isOddFormat() ? 360.0/59.0 : 360.0/60.0;
		
		// latitude zone index
		double j = Math.floor(ref_lat/Dlat) + Math.floor(0.5+(mod(ref_lat, Dlat))/Dlat-getCPREncodedLatitude()/((double)(1<<17)));
		
		// decoded position latitude
		double Rlat = Dlat*(j+getCPREncodedLatitude()/((double)(1<<17)));
		
		// longitude zone size
		double Dlon = 360.0/Math.max(1.0, NL(Rlat)-(isOddFormat()?1.0:0.0));
		
		// longitude zone coordinate
		double m =
				Math.floor(ref_lon/Dlon) +
				Math.floor(0.5+(mod(ref_lon,Dlon))/Dlon-(float)getCPREncodedLongitude()/((double)(1<<17)));
		
		// and finally the longitude
		double Rlon = Dlon * (m + getCPREncodedLongitude()/((double)(1<<17)));
		
//		System.out.println("Loc: EncLon: "+getCPREncodedLongitude()+
//				" m: "+m+" Dlon: "+Dlon+ " Rlon2: "+Rlon2);
		
		return new double[] {Rlat,Rlon}; 
	}

	/**
	 * This method converts a gray code encoded int to a standard decimal int
	 * @param gray gray code encoded int of length bitlength
	 *        bitlength bitlength of gray code
	 * @return radix 2 encoded integer
	 */
	private static int grayToBin(int gray, int bitlength) {
		int result = 0;
		for (int i = bitlength-1; i >= 0; --i)
			result = result|((((0x1<<(i+1))&result)>>>1)^((1<<i)&gray));
		return result;
	}

	/**
	 * @return the decoded altitude in meters
	 * @throws Exception if no position available
	 */
	public double getAltitude() throws Exception {
		if (!altitude_available)
			throw new Exception("No altitude information available!");

		boolean Qbit = (altitude_encoded&0x10)!=0;
		int N;
		if (Qbit) { // altitude reported in 25ft increments
			N = (altitude_encoded&0xF) | ((altitude_encoded&0xFE0)>>>1);
			return (25*N-1000)*0.3048;
		}
		else { // altitude is above 50175ft, so we use 100ft increments

			// it's decoded using the Gillham code
			int C1 = (0x800&altitude_encoded)>>>11;
			int A1 = (0x400&altitude_encoded)>>>10;
			int C2 = (0x200&altitude_encoded)>>>9;
			int A2 = (0x100&altitude_encoded)>>>8;
			int C4 = (0x080&altitude_encoded)>>>7;
			int A4 = (0x040&altitude_encoded)>>>6;
			int B1 = (0x020&altitude_encoded)>>>5;
			int B2 = (0x008&altitude_encoded)>>>3;
			int D2 = (0x004&altitude_encoded)>>>2;
			int B4 = (0x002&altitude_encoded)>>>1;
			int D4 = (0x001&altitude_encoded);

			// this is standard gray code
			int N500 = grayToBin(D2<<7|D4<<6|A1<<5|A2<<4|A4<<3|B1<<2|B2<<1|B4, 8);

			// 100-ft steps must be converted
			int N100 = grayToBin(C1<<2|C2<<1|C4, 3)-1;
			if (N100 == 6) N100=4;
			if (N500%2 != 0) N100=4-N100; // invert it

			return (-1200+N500*500+N100*100)*0.3048;
		}
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"Position:\n"+
				"\tFormat:\t\t"+(isOddFormat()?"odd":"even")+
				"\tHas position:\t"+(hasPosition()?"yes":"no")+
				"\tHas altitude:\t"+(hasAltitude()?"yes":"no");
	}

}