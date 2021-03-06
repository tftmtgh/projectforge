import classNames from 'classnames';
import PropTypes from 'prop-types';
import React from 'react';
import style from './Chevron.module.scss';

function AnimatedChevron({ direction }) {
    return (
        <div className={style.chevronContainer}>
            <span className={classNames(style.animatedChevron, style[direction.toLowerCase()])} />
        </div>
    );
}

AnimatedChevron.propTypes = {
    direction: PropTypes.oneOf(['neutral', 'ASCENDING', 'DESCENDING']),
};

AnimatedChevron.defaultProps = {
    direction: 'neutral',
};

export default AnimatedChevron;
